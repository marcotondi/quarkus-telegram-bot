package io.quarkiverse.telegram.bot.runtime.dispatcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.ChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InaccessibleMessage;
import io.quarkiverse.telegram.bot.runtime.model.InlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult;
import io.quarkiverse.telegram.bot.runtime.model.MaybeInaccessibleMessage;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;
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
    private final TelegramBotClient client;
    private final TelegramBotConfig config;
    private final SessionStore sessionStore;
    private final Map<HandlerDescriptor, Method> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SessionKey, Uni<Void>> sessionLocks = new ConcurrentHashMap<>();

    public UpdateDispatcher(
            List<HandlerDescriptor> handlers,
            TelegramBotClient client,
            TelegramBotConfig config,
            SessionStore sessionStore) {
        this.handlers = handlers != null ? List.copyOf(handlers) : List.of();
        this.client = client;
        this.config = config;
        this.sessionStore = sessionStore;
    }

    public Uni<Void> dispatch(Update update) {
        if (update == null) {
            return Uni.createFrom().voidItem();
        }

        SessionKey sessionKey = extractSessionKey(update);
        if (sessionKey == null) {
            AtomicReference<String> failingBeanClass = new AtomicReference<>();
            return processUpdate(update, null, null, failingBeanClass)
                    .onFailure().recoverWithUni(error -> handleError(error, update, null, failingBeanClass.get()));
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
        return sessionStore.acquireLock(sessionKey, Duration.ofSeconds(15))
                .flatMap(locked -> sessionStore.load(sessionKey)
                        .flatMap(snapshotOpt -> {
                            ConversationContextImpl ctx = new ConversationContextImpl(
                                    sessionKey,
                                    snapshotOpt.orElse(null),
                                    sessionStore,
                                    client.objectMapper(),
                                    config.session().ttl());
                            return processUpdate(update, sessionKey, ctx, failingBeanClass)
                                    .flatMap(v -> persistContext(ctx))
                                    .onFailure()
                                    .recoverWithUni(error -> handleError(error, update, ctx, failingBeanClass.get()));
                        }))
                .eventually(() -> sessionStore.releaseLock(sessionKey));
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
                if (h.getType() == HandlerType.INLINE_QUERY) {
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
                if (h.getType() == HandlerType.CHOSEN_INLINE_RESULT) {
                    MatchResult match = matchPattern(h.getMatchValue(), chosenInlineResult.resultId());
                    if (match.matches()) {
                        return invokeHandler(h, update, ctx, match.param(), failingBeanClass);
                    }
                }
            }
            return Uni.createFrom().voidItem();
        }

        Message message = update.message();
        CallbackQuery callbackQuery = update.callbackQuery();
        String currentState = ctx != null ? ctx.currentState().orElse(null) : null;

        // 1. Commands with interruptsState = true have absolute precedence
        if (message != null && message.text() != null && message.text().startsWith("/")) {
            String rawCommand = extractCommand(message.text());
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.COMMAND && h.isInterruptsState()
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
                            && currentState.equals(h.getStateCondition())) {
                        return invokeHandler(h, update, ctx, null, failingBeanClass);
                    }
                }
            }

            for (HandlerDescriptor h : handlers) {
                if (currentState.equals(h.getStateCondition())) {
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
            // First look for state-specific mismatch handler, then fallback/global mismatch handler
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.STATE_MISMATCH && currentState.equals(h.getStateCondition())) {
                    return invokeHandler(h, update, ctx, null, failingBeanClass);
                }
            }
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.STATE_MISMATCH && h.getStateCondition() == null) {
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
                if (h.getType() == HandlerType.CALLBACK_QUERY && h.getStateCondition() == null) {
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
                if (h.getType() == HandlerType.MESSAGE && h.getStateCondition() == null) {
                    if (matchesMessageType(h.getMessageType(), message)) {
                        return invokeHandler(h, update, ctx, null, failingBeanClass);
                    }
                }
            }
        }

        return Uni.createFrom().voidItem();
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
            if (error != null && Throwable.class.isAssignableFrom(pt) && pt.isAssignableFrom(error.getClass())) {
                args[i] = error;
            } else if (Update.class.isAssignableFrom(pt)) {
                args[i] = update;
            } else if (InlineQuery.class.isAssignableFrom(pt)) {
                args[i] = update != null ? update.inlineQuery() : null;
            } else if (ChosenInlineResult.class.isAssignableFrom(pt)) {
                args[i] = update != null ? update.chosenInlineResult() : null;
            } else if (Message.class.isAssignableFrom(pt)) {
                if (update != null && update.message() != null) {
                    args[i] = update.message();
                } else if (update != null && update.callbackQuery() != null
                        && update.callbackQuery().message() instanceof Message msg) {
                    args[i] = msg;
                } else {
                    args[i] = null;
                }
            } else if (MaybeInaccessibleMessage.class.isAssignableFrom(pt)) {
                args[i] = update != null && update.message() != null ? update.message()
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
            if (updateId != null) {
                MDC.put("telegram.update_id", String.valueOf(updateId));
            }
            if (chatId != null) {
                MDC.put("telegram.chat_id", String.valueOf(chatId));
            }
            if (userId != null) {
                MDC.put("telegram.user_id", String.valueOf(userId));
            }

            recordOpenTelemetry(rootError, updateId, chatId, userId);

            LOG.errorf(rootError,
                    "[Telegram Error] [update_id=%s, chat_id=%s, user_id=%s] Error during update dispatch: %s",
                    updateId, chatId, userId, rootError.getMessage());
        } finally {
            MDC.remove("telegram.update_id");
            MDC.remove("telegram.chat_id");
            MDC.remove("telegram.user_id");
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
        if (update.message() != null && update.message().from() != null) {
            return update.message().from();
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
        if (update.message() != null) {
            return update.message().chat();
        }
        if (update.callbackQuery() != null && update.callbackQuery().message() != null) {
            MaybeInaccessibleMessage mim = update.callbackQuery().message();
            if (mim instanceof Message m) {
                return m.chat();
            } else if (mim instanceof InaccessibleMessage im) {
                return im.chat();
            }
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
