package io.quarkiverse.telegram.bot.deployment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.runtime.api.OnError;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerDescriptor;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerType;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.smallrye.mutiny.Uni;

class HandlerValidatorTest {

    @Test
    void duplicateCommandWithoutChatTypeFilterFails() {
        HandlerBuildItem first = command("/start", "BotA", List.of());
        HandlerBuildItem second = command("/Start", "BotB", List.of());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> HandlerValidator.validateNoDuplicateCommands(List.of(first, second)));
        assertTrue(error.getMessage().contains("Duplicate Telegram command"));
    }

    @Test
    void duplicateCommandWithDisjointChatTypesIsAllowed() {
        HandlerBuildItem priv = command("/start", "BotA", List.of("private"));
        HandlerBuildItem group = command("/start", "BotB", List.of("group"));

        assertDoesNotThrow(() -> HandlerValidator.validateNoDuplicateCommands(List.of(priv, group)));
    }

    @Test
    void duplicateCommandInDifferentStatesIsAllowed() {
        HandlerBuildItem stateless = command("/next", "BotA", List.of());
        HandlerDescriptor descriptor = new HandlerDescriptor(HandlerType.COMMAND, "/next", "BotB", "next",
                List.of(), false, "WAITING", null);
        HandlerBuildItem stateful = new HandlerBuildItem(descriptor);

        assertDoesNotThrow(() -> HandlerValidator.validateNoDuplicateCommands(List.of(stateless, stateful)));
    }

    @Test
    void validSignaturePasses() {
        assertDoesNotThrow(() -> HandlerValidator.validateSignatures(index(ValidHandler.class)));
    }

    @Test
    void errorHandlerMayReceiveException() {
        assertDoesNotThrow(() -> HandlerValidator.validateSignatures(index(ErrorHandler.class)));
    }

    @Test
    void unsupportedParameterFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> HandlerValidator.validateSignatures(index(BadParameter.class)));
        assertTrue(error.getMessage().contains("unsupported parameter type java.lang.Runnable"));
    }

    @Test
    void unsupportedReturnTypeFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> HandlerValidator.validateSignatures(index(BadReturn.class)));
        assertTrue(error.getMessage().contains("returns unsupported type int"));
    }

    @Test
    void staticHandlerFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> HandlerValidator.validateSignatures(index(StaticHandler.class)));
        assertTrue(error.getMessage().contains("must be a non-static"));
    }

    @Test
    void throwableParameterOnCommandFails() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> HandlerValidator.validateSignatures(index(ThrowableOnCommand.class)));
        assertEquals(true, error.getMessage().contains("only @OnError handlers"));
    }

    private HandlerBuildItem command(String value, String className, List<String> chatTypes) {
        HandlerDescriptor descriptor = new HandlerDescriptor(HandlerType.COMMAND, value, className, "handle",
                List.of(), false, null, null);
        descriptor.setAllowedChatTypes(chatTypes);
        return new HandlerBuildItem(descriptor);
    }

    private Index index(Class<?> type) {
        Indexer indexer = new Indexer();
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            indexer.index(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return indexer.complete();
    }

    static class ValidHandler {
        @TelegramCommand("/valid")
        public SendMessage valid(User user, Chat chat, String value) {
            return null;
        }

        @TelegramCommand("/inline")
        public Uni<List<InlineQueryResult>> inline() {
            return null;
        }
    }

    static class ErrorHandler {
        @OnError(IllegalArgumentException.class)
        public String handle(IllegalArgumentException error, User user) {
            return "error";
        }
    }

    static class BadParameter {
        @TelegramCommand("/bad-param")
        public String handle(Runnable runnable) {
            return "nope";
        }
    }

    static class BadReturn {
        @TelegramCommand("/bad-return")
        public int handle() {
            return 1;
        }
    }

    static class StaticHandler {
        @TelegramCommand("/static")
        public static String handle() {
            return "static";
        }
    }

    static class ThrowableOnCommand {
        @TelegramCommand("/throwable")
        public String handle(IllegalArgumentException error) {
            return "nope";
        }
    }
}
