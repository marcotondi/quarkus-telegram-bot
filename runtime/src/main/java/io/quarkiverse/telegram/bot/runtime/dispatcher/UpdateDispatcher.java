package io.quarkiverse.telegram.bot.runtime.dispatcher;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InaccessibleMessage;
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
            return processUpdate(update, null, null);
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
        return sessionStore.acquireLock(sessionKey, Duration.ofSeconds(15))
                .flatMap(locked -> sessionStore.load(sessionKey)
                        .flatMap(snapshotOpt -> {
                            ConversationContextImpl ctx = new ConversationContextImpl(
                                    sessionKey,
                                    snapshotOpt.orElse(null),
                                    sessionStore,
                                    client.objectMapper(),
                                    config.session().ttl());
                            return processUpdate(update, sessionKey, ctx)
                                    .flatMap(v -> persistContext(ctx));
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

    private Uni<Void> processUpdate(Update update, SessionKey sessionKey, ConversationContextImpl ctx) {
        Message message = update.message();
        CallbackQuery callbackQuery = update.callbackQuery();
        String currentState = ctx != null ? ctx.currentState().orElse(null) : null;

        // 1. Commands with interruptsState = true have absolute precedence
        if (message != null && message.text() != null && message.text().startsWith("/")) {
            String rawCommand = extractCommand(message.text());
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.COMMAND && h.isInterruptsState()
                        && matchesCommand(h.getMatchValue(), rawCommand)) {
                    return invokeHandler(h, update, ctx, null);
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
                        return invokeHandler(h, update, ctx, null);
                    }
                }
            }

            for (HandlerDescriptor h : handlers) {
                if (currentState.equals(h.getStateCondition())) {
                    if (callbackQuery != null && h.getType() == HandlerType.CALLBACK_QUERY) {
                        MatchResult match = matchPattern(h.getMatchValue(), callbackQuery.data());
                        if (match.matches()) {
                            return invokeHandler(h, update, ctx, match.param());
                        }
                    } else if (message != null && h.getType() == HandlerType.MESSAGE) {
                        if (matchesMessageType(h.getMessageType(), message)) {
                            return invokeHandler(h, update, ctx, null);
                        }
                    }
                }
            }

            // Input did not match the active state handlers: check for @OnStateMismatch handler!
            // First look for state-specific mismatch handler, then fallback/global mismatch handler
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.STATE_MISMATCH && currentState.equals(h.getStateCondition())) {
                    return invokeHandler(h, update, ctx, null);
                }
            }
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.STATE_MISMATCH && h.getStateCondition() == null) {
                    return invokeHandler(h, update, ctx, null);
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
                        return invokeHandler(h, update, ctx, null);
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
                        return invokeHandler(h, update, ctx, match.param());
                    }
                }
            }
        }

        // 3c. Standard message handlers without state condition
        if (message != null) {
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.MESSAGE && h.getStateCondition() == null) {
                    if (matchesMessageType(h.getMessageType(), message)) {
                        return invokeHandler(h, update, ctx, null);
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
            String callbackParam) {
        try {
            Class<?> beanClass = Thread.currentThread().getContextClassLoader().loadClass(descriptor.getClassName());
            Object beanInstance = Arc.container().instance(beanClass).get();
            if (beanInstance == null) {
                LOG.errorf("CDI bean instance not found for class %s", descriptor.getClassName());
                return Uni.createFrom().voidItem();
            }

            Method method = getMethod(descriptor, beanClass);
            Object[] args = resolveArgs(method, descriptor, update, ctx, callbackParam);

            Object result = method.invoke(beanInstance, args);
            return handleResult(result, extractChatId(update), update);
        } catch (Exception e) {
            LOG.errorf(e, "Error executing handler %s#%s", descriptor.getClassName(), descriptor.getMethodName());
            return Uni.createFrom().failure(e);
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
            String callbackParam) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> pt = paramTypes[i];
            if (Update.class.isAssignableFrom(pt)) {
                args[i] = update;
            } else if (Message.class.isAssignableFrom(pt)) {
                if (update.message() != null) {
                    args[i] = update.message();
                } else if (update.callbackQuery() != null
                        && update.callbackQuery().message() instanceof Message msg) {
                    args[i] = msg;
                } else {
                    args[i] = null;
                }
            } else if (MaybeInaccessibleMessage.class.isAssignableFrom(pt)) {
                args[i] = update.message() != null ? update.message()
                        : (update.callbackQuery() != null ? update.callbackQuery().message() : null);
            } else if (CallbackQuery.class.isAssignableFrom(pt)) {
                args[i] = update.callbackQuery();
            } else if (User.class.isAssignableFrom(pt)) {
                args[i] = extractUser(update);
            } else if (Chat.class.isAssignableFrom(pt)) {
                args[i] = extractChat(update);
            } else if (TelegramBotClient.class.isAssignableFrom(pt)) {
                args[i] = client;
            } else if (ConversationContext.class.isAssignableFrom(pt)) {
                args[i] = ctx;
            } else if (String.class.isAssignableFrom(pt)) {
                args[i] = callbackParam;
            } else if (ctx != null && ctx.data(pt).isPresent()) {
                args[i] = ctx.data(pt).get();
            } else {
                args[i] = null;
            }
        }
        return args;
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
