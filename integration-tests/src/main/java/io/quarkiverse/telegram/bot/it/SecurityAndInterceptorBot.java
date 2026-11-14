package io.quarkiverse.telegram.bot.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.BotAdmin;
import io.quarkiverse.telegram.bot.runtime.api.ChatType;
import io.quarkiverse.telegram.bot.runtime.api.OnError;
import io.quarkiverse.telegram.bot.runtime.api.SecuredBot;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.api.TelegramSecurityException;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.User;

@ApplicationScoped
public class SecurityAndInterceptorBot {

    @BotAdmin
    @TelegramCommand("/admin-only")
    public SendMessage adminOnly(User user, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Welcome Admin " + user.id())
                .build();
    }

    @BotAdmin(silent = true)
    @TelegramCommand("/admin-silent")
    public SendMessage adminSilent(User user, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Silent Admin OK")
                .build();
    }

    @SecuredBot(roles = { "vip" }, allowedUsers = { "7777777" })
    @TelegramCommand("/vip-only")
    public SendMessage vipOnly(User user, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Welcome VIP " + user.id())
                .build();
    }

    @ChatType(ChatType.Type.PRIVATE)
    @TelegramCommand("/chat-filter")
    public SendMessage chatFilterPrivate(Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Response from PRIVATE chat")
                .build();
    }

    @ChatType({ ChatType.Type.GROUP, ChatType.Type.SUPERGROUP })
    @TelegramCommand("/chat-filter")
    public SendMessage chatFilterGroup(Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Response from GROUP chat")
                .build();
    }

    @ChatType(ChatType.Type.CHANNEL)
    @TelegramCommand("/channel-only")
    public SendMessage channelOnly(Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Response from CHANNEL")
                .build();
    }

    @OnError(TelegramSecurityException.class)
    public SendMessage handleSecurityException(TelegramSecurityException ex, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("ACCESSO NEGATO: " + ex.getMessage())
                .build();
    }
}
