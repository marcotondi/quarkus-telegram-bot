package io.quarkiverse.telegram.bot.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.deployment.devui.DevUIFsmEntry;
import io.quarkiverse.telegram.bot.deployment.devui.DevUIHandlerEntry;
import io.quarkiverse.telegram.bot.deployment.devui.TelegramBotDevUIProcessor;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerDescriptor;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerType;

public class TelegramBotDevUITest {

    @Test
    public void testHandlerEntryMappingForCommand() {
        HandlerDescriptor desc = new HandlerDescriptor(
                HandlerType.COMMAND,
                "/start",
                "com.example.GreetingBot",
                "startCommand",
                List.of("io.quarkiverse.telegram.bot.runtime.model.User"),
                false,
                null,
                null,
                "Avvia il bot");
        desc.setAllowedChatTypes(List.of("private"));
        desc.setAdminOnly(true);
        desc.setSilentSecurity(true);

        DevUIHandlerEntry entry = TelegramBotDevUIProcessor.toHandlerEntry(desc);

        assertEquals("COMMAND", entry.type());
        assertEquals("/start", entry.match());
        assertEquals("GreetingBot#startCommand", entry.targetMethod());
        assertEquals("private", entry.chatTypes());
        assertEquals("@BotAdmin (silent)", entry.security());
        assertEquals("-", entry.state());
        assertEquals("Avvia il bot", entry.description());
    }

    @Test
    public void testHandlerEntryMappingForRolesAndUsers() {
        HandlerDescriptor desc = new HandlerDescriptor(
                HandlerType.CALLBACK_QUERY,
                "admin_*",
                "com.example.AdminBot",
                "handleCallback",
                List.of(),
                false,
                "ACTIVE_ADMIN",
                null,
                null);
        desc.setRequiredRoles(List.of("admin", "moderator"));

        DevUIHandlerEntry entry = TelegramBotDevUIProcessor.toHandlerEntry(desc);
        assertEquals("CALLBACK_QUERY", entry.type());
        assertEquals("admin_*", entry.match());
        assertEquals("AdminBot#handleCallback", entry.targetMethod());
        assertEquals("ALL", entry.chatTypes());
        assertEquals("roles=[admin, moderator]", entry.security());
        assertEquals("ACTIVE_ADMIN", entry.state());

        desc.setRequiredRoles(List.of());
        desc.setAllowedUsers(List.of("123456789"));
        entry = TelegramBotDevUIProcessor.toHandlerEntry(desc);
        assertEquals("users=[123456789]", entry.security());

        desc.setAllowedUsers(List.of());
        entry = TelegramBotDevUIProcessor.toHandlerEntry(desc);
        assertEquals("public", entry.security());
    }

    @Test
    public void testHandlerEntryMappingForInterceptorsAndErrors() {
        HandlerDescriptor beforeDesc = new HandlerDescriptor(
                HandlerType.BEFORE_UPDATE,
                null,
                "com.example.AuditInterceptor",
                "audit",
                List.of());
        beforeDesc.setPriority(50);

        DevUIHandlerEntry beforeEntry = TelegramBotDevUIProcessor.toHandlerEntry(beforeDesc);
        assertEquals("BEFORE_UPDATE", beforeEntry.type());
        assertEquals("priority=50", beforeEntry.match());

        HandlerDescriptor errDesc = new HandlerDescriptor(
                HandlerType.ERROR,
                "java.lang.IllegalArgumentException",
                "com.example.GlobalErrorHandler",
                "onError",
                List.of());

        DevUIHandlerEntry errEntry = TelegramBotDevUIProcessor.toHandlerEntry(errDesc);
        assertEquals("ERROR", errEntry.type());
        assertEquals("java.lang.IllegalArgumentException", errEntry.match());
    }

    @Test
    public void testFsmEntryMapping() {
        HandlerDescriptor desc = new HandlerDescriptor(
                HandlerType.MESSAGE,
                null,
                "com.example.BookingBot",
                "receiveDate",
                List.of(),
                false,
                "WAITING_DATE",
                "TEXT",
                "Riceve la data");

        DevUIFsmEntry fsm = TelegramBotDevUIProcessor.toFsmEntry(desc);
        assertEquals("WAITING_DATE", fsm.state());
        assertEquals("MESSAGE (TEXT)", fsm.trigger());
        assertEquals("BookingBot#receiveDate", fsm.handler());
        assertEquals("NO", fsm.interruptsState());
        assertEquals("Riceve la data", fsm.description());

        HandlerDescriptor cancelDesc = new HandlerDescriptor(
                HandlerType.COMMAND,
                "/annulla",
                "com.example.BookingBot",
                "cancel",
                List.of(),
                true,
                null,
                null,
                "Annulla operazione");

        DevUIFsmEntry cancelFsm = TelegramBotDevUIProcessor.toFsmEntry(cancelDesc);
        assertEquals("(any state)", cancelFsm.state());
        assertEquals("COMMAND (/annulla)", cancelFsm.trigger());
        assertEquals("YES", cancelFsm.interruptsState());
    }
}
