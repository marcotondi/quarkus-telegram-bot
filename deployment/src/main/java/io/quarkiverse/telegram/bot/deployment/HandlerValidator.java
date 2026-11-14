package io.quarkiverse.telegram.bot.deployment;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;

import io.quarkiverse.telegram.bot.runtime.api.AfterUpdate;
import io.quarkiverse.telegram.bot.runtime.api.BeforeUpdate;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.api.OnError;
import io.quarkiverse.telegram.bot.runtime.api.OnInlineQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.OnStateMismatch;
import io.quarkiverse.telegram.bot.runtime.api.SessionPayload;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerType;

/**
 * Build-time validation of annotated handler methods: static diagnostics for invalid signatures and ambiguous commands.
 */
final class HandlerValidator {

    private static final DotName TELEGRAM_COMMAND = DotName.createSimple(TelegramCommand.class.getName());
    private static final DotName ON_MESSAGE = DotName.createSimple(OnMessage.class.getName());
    private static final DotName ON_CALLBACK_QUERY = DotName.createSimple(OnCallbackQuery.class.getName());
    private static final DotName ON_INLINE_QUERY = DotName.createSimple(OnInlineQuery.class.getName());
    private static final DotName ON_CHOSEN_INLINE_RESULT = DotName.createSimple(OnChosenInlineResult.class.getName());
    private static final DotName ON_ERROR = DotName.createSimple(OnError.class.getName());
    private static final DotName BEFORE_UPDATE = DotName.createSimple(BeforeUpdate.class.getName());
    private static final DotName AFTER_UPDATE = DotName.createSimple(AfterUpdate.class.getName());
    private static final DotName ON_STATE_MISMATCH = DotName.createSimple(OnStateMismatch.class.getName());
    private static final DotName SESSION_PAYLOAD = DotName.createSimple(SessionPayload.class.getName());

    private static final List<DotName> HANDLER_ANNOTATIONS = List.of(
            TELEGRAM_COMMAND, ON_MESSAGE, ON_CALLBACK_QUERY, ON_INLINE_QUERY, ON_CHOSEN_INLINE_RESULT,
            ON_ERROR, BEFORE_UPDATE, AFTER_UPDATE, ON_STATE_MISMATCH);

    private static final Set<DotName> INJECTABLE_PARAMS = DotNames.of(
            "io.quarkiverse.telegram.bot.runtime.model.Update",
            "io.quarkiverse.telegram.bot.runtime.model.Message",
            "io.quarkiverse.telegram.bot.runtime.model.MaybeInaccessibleMessage",
            "io.quarkiverse.telegram.bot.runtime.model.CallbackQuery",
            "io.quarkiverse.telegram.bot.runtime.model.InlineQuery",
            "io.quarkiverse.telegram.bot.runtime.model.ChosenInlineResult",
            "io.quarkiverse.telegram.bot.runtime.model.User",
            "io.quarkiverse.telegram.bot.runtime.model.Chat",
            "io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient",
            "io.quarkiverse.telegram.bot.runtime.session.ConversationContext",
            "java.lang.String",
            "java.lang.Throwable");

    private static final Set<DotName> HANDLER_RESULTS = DotNames.of(
            "java.lang.String",
            "io.quarkiverse.telegram.bot.runtime.model.SendMessage",
            "io.quarkiverse.telegram.bot.runtime.model.EditMessageText",
            "io.quarkiverse.telegram.bot.runtime.model.EditMessageReplyMarkup",
            "io.quarkiverse.telegram.bot.runtime.model.DeleteMessage",
            "io.quarkiverse.telegram.bot.runtime.model.SendChatAction",
            "io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery",
            "io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery",
            "io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult");

    private static final DotName UNI = DotName.createSimple("io.smallrye.mutiny.Uni");
    private static final DotName LIST = DotName.createSimple("java.util.List");
    private static final DotName INLINE_QUERY_RESULT = DotName
            .createSimple("io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult");
    private static final DotName VOID = DotName.createSimple("java.lang.Void");

    private HandlerValidator() {
    }

    static void validateSignatures(IndexView index) {
        Set<DotName> payloads = new HashSet<>();
        for (AnnotationInstance payload : index.getAnnotations(SESSION_PAYLOAD)) {
            payloads.add(payload.target().asClass().name());
        }

        Set<String> validated = new HashSet<>();
        for (DotName annotation : HANDLER_ANNOTATIONS) {
            for (AnnotationInstance instance : index.getAnnotations(annotation)) {
                MethodInfo method = instance.target().asMethod();
                String key = method.declaringClass().name() + "#" + method.name() + method.parameterTypes();
                if (validated.add(key)) {
                    validateMethod(method, index, payloads);
                }
            }
        }
    }

    private static void validateMethod(MethodInfo method, IndexView index, Set<DotName> payloads) {
        String location = method.declaringClass().name() + "#" + method.name();

        if (Modifier.isStatic(method.flags()) || Modifier.isPrivate(method.flags()) || Modifier.isAbstract(method.flags())) {
            throw new IllegalStateException("@Telegram handler " + location
                    + " must be a non-static, non-private, non-abstract method of a CDI bean");
        }

        boolean errorHandler = method.hasAnnotation(ON_ERROR);
        boolean beforeUpdate = method.hasAnnotation(BEFORE_UPDATE);
        boolean afterUpdate = method.hasAnnotation(AFTER_UPDATE);

        for (Type parameter : method.parameterTypes()) {
            DotName name = parameter.name();
            if (isThrowable(index, name)) {
                if (errorHandler || afterUpdate) {
                    continue;
                }
                throw new IllegalStateException("@Telegram handler " + location
                        + " declares a Throwable parameter; only @OnError handlers may receive exceptions");
            }
            if (isAllowedParameter(parameter, payloads)) {
                continue;
            }
            throw new IllegalStateException("@Telegram handler " + location + " has unsupported parameter type " + name
                    + ". Supported: Update, Message, CallbackQuery, InlineQuery, ChosenInlineResult, User, Chat,"
                    + " TelegramBotClient, ConversationContext, String and session payload types");
        }

        Type returnType = method.returnType();

        if (beforeUpdate) {
            if (isVoid(returnType) || isBoolean(returnType) || isUniOf(returnType, HandlerValidator::isBooleanOrVoid)) {
                return;
            }
            throw new IllegalStateException("@Telegram @BeforeUpdate interceptor " + location
                    + " returns unsupported type " + returnType
                    + ". Supported for @BeforeUpdate: void, boolean, Uni<Boolean>, Uni<Void>");
        }

        if (afterUpdate) {
            if (isVoid(returnType) || isUniOf(returnType, HandlerValidator::isVoid)) {
                return;
            }
            throw new IllegalStateException("@Telegram @AfterUpdate interceptor " + location
                    + " returns unsupported type " + returnType
                    + ". Supported for @AfterUpdate: void, Uni<Void>");
        }

        if (isVoid(returnType) || isValidResult(returnType)) {
            return;
        }
        throw new IllegalStateException("@Telegram handler " + location + " returns unsupported type " + returnType
                + ". Supported: void, String, SendMessage, EditMessageText, EditMessageReplyMarkup, DeleteMessage,"
                + " SendChatAction, AnswerCallbackQuery, AnswerInlineQuery, InlineQueryResult,"
                + " List<InlineQueryResult> and Uni<T> of those");
    }

    private static boolean isAllowedParameter(Type parameter, Set<DotName> payloads) {
        DotName name = parameter.name();
        if (INJECTABLE_PARAMS.contains(name) || payloads.contains(name)) {
            return true;
        }
        if (parameter.kind() == Type.Kind.PRIMITIVE || parameter.kind() == Type.Kind.ARRAY) {
            return false;
        }
        String className = name.toString();
        // Reject JDK, Jakarta, and internal extension types not listed in INJECTABLE_PARAMS
        if (className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jakarta.")
                || className.startsWith("io.quarkiverse.telegram.bot.runtime.")) {
            return false;
        }
        // Custom application records and classes are allowed as session payloads
        return true;
    }

    private static boolean isValidResult(Type type) {
        return switch (type.kind()) {
            case CLASS -> HANDLER_RESULTS.contains(type.name());
            case PARAMETERIZED_TYPE -> {
                if (type.name().equals(UNI)) {
                    Type inner = type.asParameterizedType().arguments().get(0);

                    yield isVoid(inner) || isValidResult(inner);
                }

                yield type.name().equals(LIST)
                        && type.asParameterizedType().arguments().get(0).name().equals(INLINE_QUERY_RESULT);
            }
            default -> false;
        };
    }

    private static boolean isBooleanOrVoid(Type type) {
        return isBoolean(type) || isVoid(type);
    }

    private static boolean isUniOf(Type type, Predicate<Type> innerPredicate) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE && type.name().equals(UNI)) {
            Type inner = type.asParameterizedType().arguments().get(0);
            return innerPredicate.test(inner);
        }
        return false;
    }

    private static boolean isVoid(Type type) {
        return type.kind() == Type.Kind.VOID || (type.kind() == Type.Kind.CLASS && type.name().equals(VOID));
    }

    private static boolean isBoolean(Type type) {
        return (type.kind() == Type.Kind.PRIMITIVE && type.asPrimitiveType().primitive() == PrimitiveType.Primitive.BOOLEAN)
                || (type.kind() == Type.Kind.CLASS && type.name().equals(DotName.createSimple("java.lang.Boolean")));
    }

    private static boolean isThrowable(IndexView index, DotName name) {
        DotName current = name;
        while (current != null) {
            if (current.equals(DotName.createSimple("java.lang.Throwable"))) {
                return true;
            }
            ClassInfo info = index.getClassByName(current);
            if (info == null) {
                return isThrowableAtRuntime(current);
            }
            current = info.superName();
        }
        return false;
    }

    private static boolean isThrowableAtRuntime(DotName name) {
        try {
            Class<?> type = Thread.currentThread().getContextClassLoader().loadClass(name.toString());
            return Throwable.class.isAssignableFrom(type);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static void validateNoDuplicateCommands(List<HandlerBuildItem> handlers) {
        Map<String, List<HandlerBuildItem>> byCommand = new HashMap<>();
        for (HandlerBuildItem item : handlers) {
            if (item.getDescriptor().getType() != HandlerType.COMMAND) {
                continue;
            }
            String command = item.getDescriptor().getMatchValue();
            String normalized = (command.startsWith("/") ? command.substring(1) : command).toLowerCase(Locale.ROOT);
            String key = normalized + "@" + (item.getDescriptor().getStateCondition() == null
                    ? ""
                    : item.getDescriptor().getStateCondition());
            byCommand.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        for (List<HandlerBuildItem> candidates : byCommand.values()) {
            for (int i = 0; i < candidates.size(); i++) {
                for (int j = i + 1; j < candidates.size(); j++) {
                    HandlerBuildItem first = candidates.get(i);
                    HandlerBuildItem second = candidates.get(j);
                    if (chatTypesOverlap(first, second)) {
                        throw new IllegalStateException("Duplicate Telegram command '" + first.getDescriptor().getMatchValue()
                                + "' in " + describe(first) + " and " + describe(second)
                                + ". Rename one handler or restrict them with @ChatType");
                    }
                }
            }
        }
    }

    private static boolean chatTypesOverlap(HandlerBuildItem first, HandlerBuildItem second) {
        List<String> left = first.getDescriptor().getAllowedChatTypes();
        List<String> right = second.getDescriptor().getAllowedChatTypes();
        if (left == null || left.isEmpty() || right == null || right.isEmpty()) {
            return true;
        }
        for (String type : left) {
            if (right.stream().anyMatch(type::equalsIgnoreCase)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(HandlerBuildItem item) {
        return item.getDescriptor().getClassName() + "#" + item.getDescriptor().getMethodName();
    }

    private static final class DotNames {
        private DotNames() {
        }

        static Set<DotName> of(String... names) {
            Set<DotName> result = new HashSet<>();
            for (String name : names) {
                result.add(DotName.createSimple(name));
            }
            return result;
        }
    }
}
