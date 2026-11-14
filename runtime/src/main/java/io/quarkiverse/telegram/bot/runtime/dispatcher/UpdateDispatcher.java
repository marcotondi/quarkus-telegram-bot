package io.quarkiverse.telegram.bot.runtime.dispatcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.TelegramRoleResolver;
import io.quarkiverse.telegram.bot.runtime.api.TelegramSecurityException;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.ChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.model.DeleteMessage;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageReplyMarkup;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InaccessibleMessage;
import io.quarkiverse.telegram.bot.runtime.model.InlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult;
import io.quarkiverse.telegram.bot.runtime.model.MaybeInaccessibleMessage;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendChatAction;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.quarkiverse.telegram.bot.runtime.ratelimit.RateLimiter;
import io.quarkiverse.telegram.bot.runtime.session.ConversationContext;
import io.quarkiverse.telegram.bot.runtime.session.ConversationContextImpl;
import io.quarkiverse.telegram.bot.runtime.session.SessionKey;
import io.quarkiverse.telegram.bot.runtime.session.SessionSnapshot;
import io.quarkiverse.telegram.bot.runtime.session.SessionStore;
import io.quarkus.arc.Arc;
import io.smallrye.mutiny.Uni;

public class UpdateDispatcher {

    private static final Logger LOG = Logger.getLogger(UpdateDispatcher.class);

    private final List<HandlerDescriptor> handlers;
    private final List<HandlerDescriptor> beforeInterceptors;
    private final List<HandlerDescriptor> afterInterceptors;
    private final TelegramBotClient client;
    private final TelegramBotConfig config;
    private final SessionStore sessionStore;
    private final TelegramRoleResolver roleResolver;
    private final RateLimiter rateLimiter;
    private final Map<HandlerDescriptor, Method> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SessionKey, Uni<Void>> sessionLocks = new ConcurrentHashMap<>();

    public UpdateDispatcher(
            List<HandlerDescriptor> handlers,
            TelegramBotClient client,
            TelegramBotConfig config,
            SessionStore sessionStore) {
        this(handlers, client, config, sessionStore, null);
    }

    public UpdateDispatcher(
            List<HandlerDescriptor> handlers,
            TelegramBotClient client,
            TelegramBotConfig config,
            SessionStore sessionStore,
            TelegramRoleResolver roleResolver) {
        this.client = client;
        this.config = config;
        this.sessionStore = sessionStore;
        this.roleResolver = roleResolver;
        this.rateLimiter = config.rateLimit().enabled()
                ? new RateLimiter(config.rateLimit().maxRequests(), config.rateLimit().window())
                : null;

        List<HandlerDescriptor> regular = new ArrayList<>();
        List<HandlerDescriptor> before = new ArrayList<>();
        List<HandlerDescriptor> after = new ArrayList<>();

        if (handlers != null) {
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.BEFORE_UPDATE) {
                    before.add(h);
                } else if (h.getType() == HandlerType.AFTER_UPDATE) {
                    after.add(h);
                } else {
                    regular.add(h);
                }
            }
        }

        before.sort(Comparator.comparingInt(HandlerDescriptor::getPriority));
        after.sort(Comparator.comparingInt(HandlerDescriptor::getPriority));

        this.handlers = List.copyOf(regular);
        this.beforeInterceptors = List.copyOf(before);
        this.afterInterceptors = List.copyOf(after);
    }

    public Uni<Void> dispatch(Update update) {
        if (update == null) {
            return Uni.createFrom().voidItem();
        }

        if (rateLimiter != null) {
            Long key = rateLimitKey(update);
            if (key != null) {
                rateLimiter.purgeExpired();
                RateLimiter.Decision decision = rateLimiter.check(key);
                if (decision != RateLimiter.Decision.ALLOWED) {
                    return rejectRateLimited(update, key, decision);
                }
            }
        }

        SessionKey sessionKey = extractSessionKey(update);
        if (sessionKey == null) {
            AtomicReference<String> failingBeanClass = new AtomicReference<>();
            setupMdc(update);
            return dispatchWithInterceptors(update, null, null, failingBeanClass)
                    .onTermination().invoke(this::clearMdc);
        }

        AtomicReference<Uni<Void>> ref = new AtomicReference<>();
        Uni<Void> chained = sessionLocks.compute(sessionKey, (k, previous) -> {
            Uni<Void> prev = (previous == null ? Uni.createFrom().voidItem() : previous);
            Uni<Void> next = prev
                    .onFailure().recoverWithNull()
                    .chain(() -> doDispatch(update, sessionKey))
                    .memoize().indefinitely();
            ref.set(next);
            return next;
        });

        return chained.eventually(() -> {
            Uni<Void> target = ref.get();
            if (target != null) {
                sessionLocks.computeIfPresent(sessionKey, (k, v) -> v == target ? null : v);
            }
        });
    }

    private Uni<Void> doDispatch(Update update, SessionKey sessionKey) {
        AtomicReference<String> failingBeanClass = new AtomicReference<>();
        setupMdc(update);
        return sessionStore.acquireLock(sessionKey, Duration.ofSeconds(15))
                .flatMap(locked -> sessionStore.load(sessionKey)
                        .flatMap(snapshotOpt -> {
                            ConversationContextImpl ctx = new ConversationContextImpl(
                                    sessionKey,
                                    snapshotOpt.orElse(null),
                                    sessionStore,
                                    client.objectMapper(),
                                    config.session().ttl());
                            return dispatchWithInterceptors(update, sessionKey, ctx, failingBeanClass);
                        }))
                .onTermination().call(() -> {
                    clearMdc();
                    return sessionStore.releaseLock(sessionKey);
                });
    }

    private Uni<Void> dispatchWithInterceptors(
            Update update,
            SessionKey sessionKey,
            ConversationContextImpl ctx,
            AtomicReference<String> failingBeanClass) {
        return executeBeforeInterceptors(update, ctx)
                .flatMap(proceed -> {
                    if (!proceed) {
                        dismissCallbackQueryIfPresent(update);
                        return executeAfterInterceptors(update, ctx, null);
                    }
                    return processUpdate(update, sessionKey, ctx, failingBeanClass)
                            .flatMap(v -> persistContext(ctx))
                            .onItemOrFailure().transformToUni((res, failure) -> {
                                return executeAfterInterceptors(update, ctx, failure)
                                        .chain(() -> {
                                            if (failure != null) {
                                                return handleError(failure, update, ctx, failingBeanClass.get());
                                            }
                                            return Uni.createFrom().voidItem();
                                        });
                            });
                });
    }

    private Uni<Boolean> executeBeforeInterceptors(Update update, ConversationContext ctx) {
        if (beforeInterceptors.isEmpty()) {
            return Uni.createFrom().item(true);
        }
        Uni<Boolean> uni = Uni.createFrom().item(true);
        for (HandlerDescriptor interceptor : beforeInterceptors) {
            uni = uni.flatMap(proceed -> {
                if (!proceed) {
                    return Uni.createFrom().item(false);
                }
                return invokeBeforeInterceptor(interceptor, update, ctx);
            });
        }
        return uni;
    }

    private Uni<Boolean> invokeBeforeInterceptor(
            HandlerDescriptor descriptor,
            Update update,
            ConversationContext ctx) {
        try {
            Class<?> beanClass = Thread.currentThread().getContextClassLoader().loadClass(descriptor.getClassName());
            Object beanInstance = Arc.container().instance(beanClass).get();
            if (beanInstance == null) {
                LOG.errorf("CDI bean instance not found for @BeforeUpdate class %s", descriptor.getClassName());
                return Uni.createFrom().item(true);
            }

            Method method = getMethod(descriptor, beanClass);
            Object[] args = resolveArgs(method, descriptor, update, ctx, null, null);

            Object result = method.invoke(beanInstance, args);
            if (result == null) {
                return Uni.createFrom().item(true);
            }
            if (result instanceof Boolean b) {
                return Uni.createFrom().item(b);
            }
            if (result instanceof Uni<?> resUni) {
                return resUni.map(val -> {
                    if (val instanceof Boolean b) {
                        return b;
                    }
                    return true;
                });
            }
            return Uni.createFrom().item(true);
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            return Uni.createFrom().failure(cause);
        }
    }

    private Uni<Void> executeAfterInterceptors(Update update, ConversationContext ctx, Throwable error) {
        if (afterInterceptors.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        Uni<Void> uni = Uni.createFrom().voidItem();
        for (HandlerDescriptor interceptor : afterInterceptors) {
            uni = uni.flatMap(v -> invokeAfterInterceptor(interceptor, update, ctx, error)
                    .onFailure().invoke(err -> LOG.errorf(err, "Error executing @AfterUpdate interceptor %s#%s",
                            interceptor.getClassName(), interceptor.getMethodName()))
                    .onFailure().recoverWithNull());
        }
        return uni;
    }

    private Uni<Void> invokeAfterInterceptor(
            HandlerDescriptor descriptor,
            Update update,
            ConversationContext ctx,
            Throwable error) {
        try {
            Class<?> beanClass = Thread.currentThread().getContextClassLoader().loadClass(descriptor.getClassName());
            Object beanInstance = Arc.container().instance(beanClass).get();
            if (beanInstance == null) {
                LOG.errorf("CDI bean instance not found for @AfterUpdate class %s", descriptor.getClassName());
                return Uni.createFrom().voidItem();
            }

            Method method = getMethod(descriptor, beanClass);
            Object[] args = resolveArgs(method, descriptor, update, ctx, null, error);

            Object result = method.invoke(beanInstance, args);
            if (result instanceof Uni<?> resUni) {
                return resUni.replaceWithVoid();
            }
            return Uni.createFrom().voidItem();
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            return Uni.createFrom().failure(cause);
        }
    }

    private Long rateLimitKey(Update update) {
        if (config.rateLimit().scope() == TelegramBotConfig.RateLimitScope.CHAT) {
            Long chatId = extractChatId(update);
            return chatId != null ? chatId : userId(update);
        }
        Long userId = userId(update);
        return userId != null ? userId : extractChatId(update);
    }

    private Long userId(Update update) {
        User user = extractUser(update);
        return user != null ? user.id() : null;
    }

    private Uni<Void> rejectRateLimited(Update update, Long key, RateLimiter.Decision decision) {
        LOG.warnf("[Telegram RateLimit] Update %s rejected for key %s (%s)",
                update.updateId(), key, config.rateLimit().scope());
        dismissCallbackQueryIfPresent(update);
        Long chatId = extractChatId(update);
        String message = config.rateLimit().message().orElse(null);
        if (decision != RateLimiter.Decision.LIMITED || chatId == null || message == null || message.isBlank()) {
            return Uni.createFrom().voidItem();
        }
        return client.sendMessage(chatId, message)
                .replaceWithVoid()
                .onFailure().recoverWithNull();
    }

    private void dismissCallbackQueryIfPresent(Update update) {
        if (update != null && update.callbackQuery() != null) {
            client.answerCallbackQuery(update.callbackQuery().id())
                    .replaceWithVoid()
                    .onFailure().recoverWithNull()
                    .subscribe().with(v -> {
                    }, err -> {
                    });
        }
    }

    private void setupMdc(Update update) {
        Long updateId = update != null ? (long) update.updateId() : null;
        Long chatId = extractChatId(update);
        User user = extractUser(update);
        Long userId = user != null ? user.id() : null;
        if (updateId != null) {
            MDC.put("telegram.update_id", String.valueOf(updateId));
        }
        if (chatId != null) {
            MDC.put("telegram.chat_id", String.valueOf(chatId));
        }
        if (userId != null) {
            MDC.put("telegram.user_id", String.valueOf(userId));
        }
    }

    private void clearMdc() {
        MDC.remove("telegram.update_id");
        MDC.remove("telegram.chat_id");
        MDC.remove("telegram.user_id");
    }

    private Uni<Void> persistContext(ConversationContextImpl ctx) {
        if (ctx == null || !ctx.isModified()) {
            return Uni.createFrom().voidItem();
        }
        if (ctx.isCompleted() || ctx.getCurrentState() == null) {
            return ctx.getSessionStore().delete(ctx.getSessionKey());
        }
        try {
            String payloadJson = null;
            String payloadType = null;
            if (ctx.getCurrentPayload() != null) {
                payloadType = ctx.getCurrentPayload().getClass().getName();
                payloadJson = ctx.getObjectMapper().writeValueAsString(ctx.getCurrentPayload());
            }
            SessionSnapshot snapshot = new SessionSnapshot(ctx.getCurrentState(), payloadJson, payloadType);
            return ctx.getSessionStore().save(ctx.getSessionKey(), snapshot, ctx.getTtl());
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize session snapshot for %s", ctx.getSessionKey());
            return Uni.createFrom().voidItem();
        }
    }

    private Uni<Void> processUpdate(
            Update update,
            SessionKey sessionKey,
            ConversationContextImpl ctx,
            AtomicReference<String> failingBeanClass) {
        InlineQuery inlineQuery = update.inlineQuery();
        ChosenInlineResult chosenInlineResult = update.chosenInlineResult();

        if (inlineQuery != null) {
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.INLINE_QUERY && matchesChatType(h, update)) {
                    MatchResult match = matchPattern(h.getMatchValue(), inlineQuery.query());
                    if (match.matches()) {
                        return invokeHandler(h, update, ctx, match.param(), failingBeanClass);
                    }
                }
            }
            return Uni.createFrom().voidItem();
        }

        if (chosenInlineResult != null) {
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.CHOSEN_INLINE_RESULT && matchesChatType(h, update)) {
                    MatchResult match = matchPattern(h.getMatchValue(), chosenInlineResult.resultId());
                    if (match.matches()) {
                        return invokeHandler(h, update, ctx, match.param(), failingBeanClass);
                    }
                }
            }
            return Uni.createFrom().voidItem();
        }

        Message message = extractMessage(update);
        CallbackQuery callbackQuery = update.callbackQuery();
        String currentState = ctx != null ? ctx.currentState().orElse(null) : null;

        // 1. Commands with interruptsState = true have absolute precedence
        if (message != null && message.text() != null && message.text().startsWith("/")) {
            String rawCommand = extractCommand(message.text());
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.COMMAND && h.isInterruptsState()
                        && matchesChatType(h, update)
                        && matchesCommand(h.getMatchValue(), rawCommand)) {
                    return invokeHandler(h, update, ctx, null, failingBeanClass);
                }
            }
        }

        // 2. Active state handlers (if in an active state)
        if (currentState != null) {
            // Check commands bound to this specific state
            if (message != null && message.text() != null && message.text().startsWith("/")) {
                String rawCommand = extractCommand(message.text());
                for (HandlerDescriptor h : handlers) {
                    if (h.getType() == HandlerType.COMMAND && matchesCommand(h.getMatchValue(), rawCommand)
                            && currentState.equals(h.getStateCondition())
                            && matchesChatType(h, update)) {
                        return invokeHandler(h, update, ctx, null, failingBeanClass);
                    }
                }
            }

            for (HandlerDescriptor h : handlers) {
                if (currentState.equals(h.getStateCondition()) && matchesChatType(h, update)) {
                    if (callbackQuery != null && h.getType() == HandlerType.CALLBACK_QUERY) {
                        MatchResult match = matchPattern(h.getMatchValue(), callbackQuery.data());
                        if (match.matches()) {
                            return invokeHandler(h, update, ctx, match.param(), failingBeanClass);
                        }
                    } else if (message != null && h.getType() == HandlerType.MESSAGE) {
                        if (matchesMessageType(h.getMessageType(), message)) {
                            return invokeHandler(h, update, ctx, null, failingBeanClass);
                        }
                    }
                }
            }

            // Input did not match the active state handlers: check for @OnStateMismatch handler!
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.STATE_MISMATCH && currentState.equals(h.getStateCondition())
                        && matchesChatType(h, update)) {
                    return invokeHandler(h, update, ctx, null, failingBeanClass);
                }
            }
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.STATE_MISMATCH && h.getStateCondition() == null
                        && matchesChatType(h, update)) {
                    return invokeHandler(h, update, ctx, null, failingBeanClass);
                }
            }

            // If no state mismatch handler, per architecture doc, don't fall through to stateless standard handlers
            LOG.debugf("Ignored update for state %s because no matching state handler or mismatch handler found", currentState);
            return Uni.createFrom().voidItem();
        }

        // 3. Stateless handlers (currentState == null)
        // 3a. Commands without state condition
        if (message != null && message.text() != null && message.text().startsWith("/")) {
            String rawCommand = extractCommand(message.text());
            if (rawCommand != null) {
                for (HandlerDescriptor h : handlers) {
                    if (h.getType() == HandlerType.COMMAND && h.getStateCondition() == null
                            && matchesChatType(h, update)
                            && matchesCommand(h.getMatchValue(), rawCommand)) {
                        return invokeHandler(h, update, ctx, null, failingBeanClass);
                    }
                }
            }
            return Uni.createFrom().voidItem();
        }

        // 3b. Callback queries without state condition
        if (callbackQuery != null) {
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.CALLBACK_QUERY && h.getStateCondition() == null
                        && matchesChatType(h, update)) {
                    MatchResult match = matchPattern(h.getMatchValue(), callbackQuery.data());
                    if (match.matches()) {
                        return invokeHandler(h, update, ctx, match.param(), failingBeanClass);
                    }
                }
            }
        }

        // 3c. Standard message handlers without state condition
        if (message != null) {
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.MESSAGE && h.getStateCondition() == null
                        && matchesChatType(h, update)) {
                    if (matchesMessageType(h.getMessageType(), message)) {
                        return invokeHandler(h, update, ctx, null, failingBeanClass);
                    }
                }
            }
        }

        return Uni.createFrom().voidItem();
    }

    private boolean matchesChatType(HandlerDescriptor h, Update update) {
        if (h.getAllowedChatTypes() == null || h.getAllowedChatTypes().isEmpty()) {
            return true;
        }
        Chat chat = extractChat(update);
        if (chat == null || chat.type() == null) {
            return false;
        }
        for (String allowed : h.getAllowedChatTypes()) {
            if (allowed.equalsIgnoreCase(chat.type())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkSecurity(HandlerDescriptor descriptor, Update update) {
        if (descriptor.getType() == HandlerType.ERROR) {
            return true;
        }

        boolean hasSecurity = descriptor.isAdminOnly()
                || !descriptor.getRequiredRoles().isEmpty()
                || !descriptor.getAllowedUsers().isEmpty();
        if (!hasSecurity) {
            return true;
        }

        User user = extractUser(update);
        Chat chat = extractChat(update);
        if (user == null) {
            return false;
        }

        // 1. Check allowedUsers
        if (!descriptor.getAllowedUsers().isEmpty()) {
            String userIdStr = String.valueOf(user.id());
            String username = user.username();
            String usernameWithAt = username != null ? "@" + username : null;

            for (String allowed : descriptor.getAllowedUsers()) {
                if (allowed.equals(userIdStr)) {
                    return true;
                }
                if (username != null && allowed.equalsIgnoreCase(username)) {
                    return true;
                }
                if (usernameWithAt != null && allowed.equalsIgnoreCase(usernameWithAt)) {
                    return true;
                }
            }
        }

        // 2. Check roles (adminOnly or requiredRoles)
        Set<String> userRoles = resolveRoles(user, chat);

        if (descriptor.isAdminOnly()) {
            if (userRoles.contains("admin")) {
                return true;
            }
        }

        if (!descriptor.getRequiredRoles().isEmpty()) {
            for (String role : descriptor.getRequiredRoles()) {
                if (userRoles.contains(role)) {
                    return true;
                }
            }
        }

        return false;
    }

    private Set<String> resolveRoles(User user, Chat chat) {
        Set<String> roles = new HashSet<>();
        if (user != null && config != null && config.security() != null && config.security().adminIds().isPresent()) {
            List<Long> adminIds = config.security().adminIds().get();
            if (adminIds.contains(user.id())) {
                roles.add("admin");
            }
        }
        if (roleResolver != null && user != null) {
            try {
                Set<String> customRoles = roleResolver.getRoles(user, chat);
                if (customRoles != null) {
                    roles.addAll(customRoles);
                }
            } catch (Exception e) {
                LOG.errorf(e, "Error resolving roles from TelegramRoleResolver for user %s", user.id());
            }
        }
        return roles;
    }

    private Uni<Void> invokeHandler(
            HandlerDescriptor descriptor,
            Update update,
            ConversationContext ctx,
            String callbackParam,
            AtomicReference<String> failingBeanClass) {
        return invokeHandler(descriptor, update, ctx, callbackParam, null, failingBeanClass);
    }

    private Uni<Void> invokeHandler(
            HandlerDescriptor descriptor,
            Update update,
            ConversationContext ctx,
            String callbackParam,
            Throwable error,
            AtomicReference<String> failingBeanClass) {
        if (!checkSecurity(descriptor, update)) {
            User user = extractUser(update);
            Long userId = user != null ? user.id() : null;
            Long chatId = extractChatId(update);
            LOG.warnf("[Telegram Security] Access denied for user %s to %s#%s",
                    userId, descriptor.getClassName(), descriptor.getMethodName());

            if (descriptor.isSilentSecurity()) {
                if (update != null && update.callbackQuery() != null) {
                    return client.answerCallbackQuery(update.callbackQuery().id())
                            .replaceWithVoid()
                            .onFailure().recoverWithNull();
                }
                return Uni.createFrom().voidItem();
            }

            TelegramSecurityException secEx = new TelegramSecurityException(
                    "Access denied for user " + userId + " to " + descriptor.getClassName() + "#" + descriptor.getMethodName(),
                    userId, chatId, descriptor.isAdminOnly() ? "admin"
                            : (descriptor.getRequiredRoles().isEmpty() ? null : descriptor.getRequiredRoles().get(0)));
            if (failingBeanClass != null) {
                failingBeanClass.set(descriptor.getClassName());
            }
            return Uni.createFrom().failure(secEx);
        }

        try {
            Class<?> beanClass = Thread.currentThread().getContextClassLoader().loadClass(descriptor.getClassName());
            Object beanInstance = Arc.container().instance(beanClass).get();
            if (beanInstance == null) {
                LOG.errorf("CDI bean instance not found for class %s", descriptor.getClassName());
                return Uni.createFrom().voidItem();
            }

            Method method = getMethod(descriptor, beanClass);
            Object[] args = resolveArgs(method, descriptor, update, ctx, callbackParam, error);

            Object result = method.invoke(beanInstance, args);
            Uni<Void> resultUni = handleResult(result, extractChatId(update), update);
            return resultUni.onFailure().invoke(err -> {
                if (failingBeanClass != null) {
                    failingBeanClass.set(descriptor.getClassName());
                }
            });
        } catch (Exception e) {
            if (failingBeanClass != null) {
                failingBeanClass.set(descriptor.getClassName());
            }
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
            return Uni.createFrom().failure(cause);
        }
    }

    private Uni<Void> handleResult(Object result, Long chatId, Update update) {
        if (result == null) {
            if (update != null && update.callbackQuery() != null) {
                return client.answerCallbackQuery(update.callbackQuery().id()).replaceWithVoid().onFailure().recoverWithNull();
            }
            return Uni.createFrom().voidItem();
        }
        if (result instanceof Uni<?> uni) {
            return uni.flatMap(val -> handleResult(val, chatId, update));
        }
        if (result instanceof EditMessageText editMsg) {
            if (editMsg.chatId() == null && editMsg.inlineMessageId() == null && update != null
                    && update.callbackQuery() != null) {
                if (update.callbackQuery().message() instanceof Message msg) {
                    editMsg = new EditMessageText(msg.chat().id(), msg.messageId(), null, editMsg.text(), editMsg.parseMode(),
                            editMsg.replyMarkup());
                } else if (update.callbackQuery().inlineMessageId() != null) {
                    editMsg = new EditMessageText(null, null, update.callbackQuery().inlineMessageId(), editMsg.text(),
                            editMsg.parseMode(), editMsg.replyMarkup());
                }
            }
            Uni<Void> editUni = client.editMessageText(editMsg).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(editUni, update);
        }
        if (result instanceof AnswerCallbackQuery answerCb) {
            if ((answerCb.callbackQueryId() == null || answerCb.callbackQueryId().isBlank()) && update != null
                    && update.callbackQuery() != null) {
                answerCb = new AnswerCallbackQuery(update.callbackQuery().id(), answerCb.text(), answerCb.showAlert(),
                        answerCb.url(), answerCb.cacheTime());
            }
            return client.answerCallbackQuery(answerCb).replaceWithVoid();
        }
        if (result instanceof AnswerInlineQuery answerInline) {
            if ((answerInline.inlineQueryId() == null || answerInline.inlineQueryId().isBlank())
                    && update != null && update.inlineQuery() != null) {
                answerInline = new AnswerInlineQuery(
                        update.inlineQuery().id(),
                        answerInline.results(),
                        answerInline.cacheTime(),
                        answerInline.isPersonal(),
                        answerInline.nextOffset(),
                        answerInline.button());
            }
            return client.answerInlineQuery(answerInline).replaceWithVoid();
        }
        if (result instanceof List<?> list && update != null && update.inlineQuery() != null) {
            if (!list.isEmpty() && list.getFirst() instanceof InlineQueryResult) {
                @SuppressWarnings("unchecked")
                List<InlineQueryResult> results = (List<InlineQueryResult>) list;
                return client.answerInlineQuery(new AnswerInlineQuery(update.inlineQuery().id(), results)).replaceWithVoid();
            } else if (list.isEmpty()) {
                return client.answerInlineQuery(new AnswerInlineQuery(update.inlineQuery().id(), List.of())).replaceWithVoid();
            }
        }
        if (result instanceof InlineQueryResult singleResult && update != null && update.inlineQuery() != null) {
            return client.answerInlineQuery(new AnswerInlineQuery(update.inlineQuery().id(), List.of(singleResult)))
                    .replaceWithVoid();
        }
        if (result instanceof SendMessage sendMessage) {
            Uni<Void> sendUni = client.sendMessage(sendMessage).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(sendUni, update);
        }
        if (result instanceof EditMessageText editMessageText) {
            Uni<Void> editUni = client.editMessageText(editMessageText).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(editUni, update);
        }
        if (result instanceof EditMessageReplyMarkup editReplyMarkup) {
            Uni<Void> editUni = client.editMessageReplyMarkup(editReplyMarkup).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(editUni, update);
        }
        if (result instanceof SendChatAction sendChatAction) {
            Uni<Void> actionUni = client.sendChatAction(sendChatAction).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(actionUni, update);
        }
        if (result instanceof DeleteMessage deleteMessage) {
            Uni<Void> delUni = client.deleteMessage(deleteMessage).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(delUni, update);
        }
        if (result instanceof String text && chatId != null) {
            Uni<Void> sendUni = client.sendMessage(chatId, text).replaceWithVoid();
            return dismissCallbackQueryAndPropagate(sendUni, update);
        }
        if (update != null && update.callbackQuery() != null) {
            return client.answerCallbackQuery(update.callbackQuery().id()).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    private Uni<Void> dismissCallbackQueryAndPropagate(Uni<Void> actionUni, Update update) {
        if (update == null || update.callbackQuery() == null) {
            return actionUni;
        }
        String callbackQueryId = update.callbackQuery().id();
        return actionUni.onItemOrFailure().transformToUni((item, failure) -> {
            Uni<Void> answerUni = client.answerCallbackQuery(callbackQueryId)
                    .replaceWithVoid()
                    .onFailure().recoverWithNull();
            if (failure != null) {
                return answerUni.chain(() -> Uni.createFrom().failure(failure));
            }
            return answerUni;
        });
    }

    private Method getMethod(HandlerDescriptor descriptor, Class<?> beanClass) throws NoSuchMethodException {
        Method cached = methodCache.get(descriptor);
        if (cached != null) {
            return cached;
        }

        List<String> targetTypes = descriptor.getParamTypes();
        Class<?> current = beanClass;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(descriptor.getMethodName())
                        && m.getParameterCount() == targetTypes.size()) {
                    boolean typesMatch = true;
                    Class<?>[] pTypes = m.getParameterTypes();
                    for (int i = 0; i < pTypes.length; i++) {
                        if (!pTypes[i].getName().equals(targetTypes.get(i))) {
                            typesMatch = false;
                            break;
                        }
                    }
                    if (typesMatch) {
                        try {
                            m.setAccessible(true);
                        } catch (Exception ignored) {
                        }
                        methodCache.put(descriptor, m);
                        return m;
                    }
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(beanClass.getName() + "#" + descriptor.getMethodName());
    }

    private Object[] resolveArgs(
            Method method,
            HandlerDescriptor descriptor,
            Update update,
            ConversationContext ctx,
            String callbackParam,
            Throwable error) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pt = paramTypes[i];
            if (Throwable.class.isAssignableFrom(pt)) {
                if (error != null && pt.isAssignableFrom(error.getClass())) {
                    args[i] = error;
                } else {
                    args[i] = null;
                }
            } else if (Update.class.isAssignableFrom(pt)) {
                args[i] = update;
            } else if (InlineQuery.class.isAssignableFrom(pt)) {
                args[i] = update != null ? update.inlineQuery() : null;
            } else if (ChosenInlineResult.class.isAssignableFrom(pt)) {
                args[i] = update != null ? update.chosenInlineResult() : null;
            } else if (Message.class.isAssignableFrom(pt)) {
                Message m = extractMessage(update);
                if (m != null) {
                    args[i] = m;
                } else if (update != null && update.callbackQuery() != null
                        && update.callbackQuery().message() instanceof Message msg) {
                    args[i] = msg;
                } else {
                    args[i] = null;
                }
            } else if (MaybeInaccessibleMessage.class.isAssignableFrom(pt)) {
                Message m = extractMessage(update);
                args[i] = m != null ? m
                        : (update != null && update.callbackQuery() != null ? update.callbackQuery().message() : null);
            } else if (CallbackQuery.class.isAssignableFrom(pt)) {
                args[i] = update != null ? update.callbackQuery() : null;
            } else if (User.class.isAssignableFrom(pt)) {
                args[i] = extractUser(update);
            } else if (Chat.class.isAssignableFrom(pt)) {
                args[i] = extractChat(update);
            } else if (TelegramBotClient.class.isAssignableFrom(pt)) {
                args[i] = client;
            } else if (ConversationContext.class.isAssignableFrom(pt)) {
                args[i] = ctx;
            } else if (String.class.isAssignableFrom(pt)) {
                if (update != null && update.inlineQuery() != null) {
                    args[i] = callbackParam != null ? callbackParam : update.inlineQuery().query();
                } else if (update != null && update.chosenInlineResult() != null) {
                    args[i] = callbackParam != null ? callbackParam : update.chosenInlineResult().resultId();
                } else {
                    args[i] = callbackParam;
                }
            } else if (ctx != null && ctx.data(pt).isPresent()) {
                args[i] = ctx.data(pt).get();
            } else {
                args[i] = null;
            }
        }
        return args;
    }

    private Uni<Void> handleError(Throwable error, Update update, ConversationContext ctx, String failingBeanClass) {
        Throwable rootError = error;
        while (rootError instanceof InvocationTargetException ite && ite.getCause() != null) {
            rootError = ite.getCause();
        }
        while (rootError instanceof CompletionException ce && ce.getCause() != null) {
            rootError = ce.getCause();
        }

        Long updateId = update != null ? (long) update.updateId() : null;
        Long chatId = extractChatId(update);
        User user = extractUser(update);
        Long userId = user != null ? user.id() : null;

        try {
            setupMdc(update);
            recordOpenTelemetry(rootError, updateId, chatId, userId);

            if (rootError instanceof SecurityException) {
                LOG.warnf("[Telegram Security] [update_id=%s, chat_id=%s, user_id=%s] %s",
                        updateId, chatId, userId, rootError.getMessage());
            } else {
                LOG.errorf(rootError,
                        "[Telegram Error] [update_id=%s, chat_id=%s, user_id=%s] Error during update dispatch: %s",
                        updateId, chatId, userId, rootError.getMessage());
            }
        } finally {
            clearMdc();
        }

        final Throwable finalError = rootError;
        HandlerDescriptor bestHandler = findBestErrorHandler(finalError, failingBeanClass);
        if (bestHandler == null) {
            if (update != null && update.callbackQuery() != null) {
                return client.answerCallbackQuery(update.callbackQuery().id())
                        .replaceWithVoid()
                        .onFailure().recoverWithNull()
                        .chain(() -> Uni.createFrom().failure(finalError));
            }
            return Uni.createFrom().failure(finalError);
        }

        return invokeHandler(bestHandler, update, ctx, null, finalError, null)
                .onFailure().invoke(handlerErr -> LOG.errorf(handlerErr, "Error executing @OnError handler %s#%s",
                        bestHandler.getClassName(), bestHandler.getMethodName()));
    }

    private void recordOpenTelemetry(Throwable error, Long updateId, Long chatId, Long userId) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> spanClass = cl.loadClass("io.opentelemetry.api.trace.Span");
            Method currentMethod = spanClass.getMethod("current");
            Object currentSpan = currentMethod.invoke(null);
            if (currentSpan != null) {
                Method setAttribute = spanClass.getMethod("setAttribute", String.class, String.class);
                if (updateId != null) {
                    setAttribute.invoke(currentSpan, "telegram.update_id", String.valueOf(updateId));
                }
                if (chatId != null) {
                    setAttribute.invoke(currentSpan, "telegram.chat_id", String.valueOf(chatId));
                }
                if (userId != null) {
                    setAttribute.invoke(currentSpan, "telegram.user_id", String.valueOf(userId));
                }
                Method recordException = spanClass.getMethod("recordException", Throwable.class);
                recordException.invoke(currentSpan, error);
            }
        } catch (Throwable ignored) {
            // OpenTelemetry API not active or not present on classpath
        }
    }

    private HandlerDescriptor findBestErrorHandler(Throwable rootError, String failingBeanClass) {
        HandlerDescriptor best = null;
        int bestDistance = Integer.MAX_VALUE;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        for (HandlerDescriptor h : handlers) {
            if (h.getType() == HandlerType.ERROR) {
                try {
                    Class<?> targetClass = cl.loadClass(h.getMatchValue());
                    int dist = exceptionDistance(rootError.getClass(), targetClass);
                    if (dist >= 0) {
                        int effectiveDist = dist
                                + (failingBeanClass != null && failingBeanClass.equals(h.getClassName()) ? 0 : 1000);
                        if (effectiveDist < bestDistance) {
                            bestDistance = effectiveDist;
                            best = h;
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        return best;
    }

    private int exceptionDistance(Class<?> actualClass, Class<?> handlerTargetClass) {
        if (!handlerTargetClass.isAssignableFrom(actualClass)) {
            return -1;
        }
        int distance = 0;
        Class<?> current = actualClass;
        while (current != null) {
            if (current.equals(handlerTargetClass)) {
                return distance;
            }
            distance++;
            current = current.getSuperclass();
        }
        return Integer.MAX_VALUE;
    }

    private SessionKey extractSessionKey(Update update) {
        Long chatId = extractChatId(update);
        User user = extractUser(update);
        if (chatId != null && user != null) {
            return new SessionKey(chatId, user.id());
        }
        return null;
    }

    private Long extractChatId(Update update) {
        Chat chat = extractChat(update);
        return chat != null ? chat.id() : null;
    }

    private User extractUser(Update update) {
        if (update == null) {
            return null;
        }
        Message m = extractMessage(update);
        if (m != null && m.from() != null) {
            return m.from();
        }
        if (update.callbackQuery() != null && update.callbackQuery().from() != null) {
            return update.callbackQuery().from();
        }
        if (update.inlineQuery() != null && update.inlineQuery().from() != null) {
            return update.inlineQuery().from();
        }
        if (update.chosenInlineResult() != null && update.chosenInlineResult().from() != null) {
            return update.chosenInlineResult().from();
        }
        return null;
    }

    private Chat extractChat(Update update) {
        if (update == null) {
            return null;
        }
        Message m = extractMessage(update);
        if (m != null) {
            return m.chat();
        }
        if (update.callbackQuery() != null && update.callbackQuery().message() != null) {
            MaybeInaccessibleMessage mim = update.callbackQuery().message();
            if (mim instanceof Message msg) {
                return msg.chat();
            } else if (mim instanceof InaccessibleMessage im) {
                return im.chat();
            }
        }
        return null;
    }

    private Message extractMessage(Update update) {
        if (update == null) {
            return null;
        }
        if (update.message() != null) {
            return update.message();
        }
        if (update.channelPost() != null) {
            return update.channelPost();
        }
        if (update.editedMessage() != null) {
            return update.editedMessage();
        }
        if (update.editedChannelPost() != null) {
            return update.editedChannelPost();
        }
        return null;
    }

    private String extractCommand(String text) {
        String firstWord = text.trim().split("\\s+")[0];
        int atIndex = firstWord.indexOf('@');
        if (atIndex > 0) {
            String command = firstWord.substring(0, atIndex);
            String targetBot = firstWord.substring(atIndex + 1);
            if (config.botUsername().isPresent()) {
                String myUsername = config.botUsername().get();
                if (!targetBot.equalsIgnoreCase(myUsername)) {
                    // Command is addressed to another bot, do not match
                    return null;
                }
            }
            return command;
        }
        return firstWord;
    }

    private boolean matchesCommand(String configCommand, String actualCommand) {
        if (configCommand == null || actualCommand == null) {
            return false;
        }
        String c1 = configCommand.startsWith("/") ? configCommand : "/" + configCommand;
        String c2 = actualCommand.startsWith("/") ? actualCommand : "/" + actualCommand;
        return c1.equalsIgnoreCase(c2);
    }

    private boolean matchesMessageType(String messageTypeStr, Message message) {
        if (messageTypeStr == null) {
            return true;
        }
        MessageType type;
        try {
            type = MessageType.valueOf(messageTypeStr);
        } catch (Exception e) {
            return true;
        }

        if (message == null) {
            return false;
        }

        return switch (type) {
            case TEXT -> message.text() != null;
            case PHOTO -> message.photo() != null && !message.photo().isEmpty();
            case DOCUMENT -> message.document() != null;
            case VOICE -> message.voice() != null;
            case LOCATION -> message.location() != null;
            case ANY -> true;
        };
    }

    private record MatchResult(boolean matches, String param) {
    }

    private MatchResult matchPattern(String patternStr, String data) {
        if (patternStr == null || patternStr.equals("*") || patternStr.isEmpty()) {
            return new MatchResult(true, data);
        }
        if (data == null) {
            return new MatchResult(false, null);
        }
        if (patternStr.endsWith("*")) {
            String prefix = patternStr.substring(0, patternStr.length() - 1);
            if (data.startsWith(prefix)) {
                return new MatchResult(true, data.substring(prefix.length()));
            }
        } else if (patternStr.equals(data)) {
            return new MatchResult(true, data);
        }
        return new MatchResult(false, null);
    }
}
