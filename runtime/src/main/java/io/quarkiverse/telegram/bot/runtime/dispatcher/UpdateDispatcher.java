package io.quarkiverse.telegram.bot.runtime.dispatcher;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;

import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
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

        return sessionStore.load(sessionKey)
                .flatMap(snapshotOpt -> {
                    ConversationContextImpl ctx = new ConversationContextImpl(
                            sessionKey,
                            snapshotOpt.orElse(null),
                            sessionStore,
                            client.objectMapper(),
                            config.session().ttl());
                    return processUpdate(update, sessionKey, ctx)
                            .flatMap(v -> persistContext(ctx));
                });
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

        // 1. Check commands
        if (message != null && message.text() != null && message.text().startsWith("/")) {
            String rawCommand = extractCommand(message.text());
            for (HandlerDescriptor h : handlers) {
                if (h.getType() == HandlerType.COMMAND && matchesCommand(h.getMatchValue(), rawCommand)) {
                    if (h.getStateCondition() != null && !h.getStateCondition().equals(currentState)) {
                        continue;
                    }
                    if (h.isInterruptsState() && ctx != null) {
                        ctx.complete();
                    }
                    return invokeHandler(h, update, ctx, null);
                }
            }
        }

        // 2. Check active state handlers first (if in an active state)
        if (currentState != null) {
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
        }

        // 3. Callback queries without state condition
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

        // 4. Standard message handlers without state condition
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
            return handleResult(result, extractChatId(update));
        } catch (Exception e) {
            LOG.errorf(e, "Error executing handler %s#%s", descriptor.getClassName(), descriptor.getMethodName());
            return Uni.createFrom().failure(e);
        }
    }

    private Uni<Void> handleResult(Object result, Long chatId) {
        if (result == null) {
            return Uni.createFrom().voidItem();
        }
        if (result instanceof Uni<?> uni) {
            return uni.flatMap(val -> handleResult(val, chatId));
        }
        if (result instanceof SendMessage sendMessage) {
            return client.sendMessage(sendMessage).replaceWithVoid();
        }
        if (result instanceof String text && chatId != null) {
            return client.sendMessage(chatId, text).replaceWithVoid();
        }
        return Uni.createFrom().voidItem();
    }

    private Method getMethod(HandlerDescriptor descriptor, Class<?> beanClass) throws NoSuchMethodException {
        Method cached = methodCache.get(descriptor);
        if (cached != null) {
            return cached;
        }

        for (Method m : beanClass.getMethods()) {
            if (m.getName().equals(descriptor.getMethodName())
                    && m.getParameterCount() == descriptor.getParamTypes().size()) {
                methodCache.put(descriptor, m);
                return m;
            }
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
        if (update.message() != null && update.message().chat() != null) {
            return update.message().chat().id();
        }
        if (update.callbackQuery() != null && update.callbackQuery().message() != null
                && update.callbackQuery().message().chat() != null) {
            return update.callbackQuery().message().chat().id();
        }
        return null;
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
            return update.callbackQuery().message().chat();
        }
        return null;
    }

    private String extractCommand(String text) {
        String firstWord = text.trim().split("\\s+")[0];
        int atIndex = firstWord.indexOf('@');
        return atIndex > 0 ? firstWord.substring(0, atIndex) : firstWord;
    }

    private boolean matchesCommand(String configCommand, String actualCommand) {
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

        return switch (type) {
            case TEXT -> message.text() != null;
            case ANY -> true;
            default -> true;
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
