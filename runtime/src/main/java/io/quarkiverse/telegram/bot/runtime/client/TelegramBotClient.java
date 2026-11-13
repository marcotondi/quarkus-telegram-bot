package io.quarkiverse.telegram.bot.runtime.client;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.model.BotCommand;
import io.quarkiverse.telegram.bot.runtime.model.GetUpdatesRequest;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.SetMyCommandsRequest;
import io.quarkiverse.telegram.bot.runtime.model.SetWebhookRequest;
import io.quarkiverse.telegram.bot.runtime.model.TelegramResponse;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class TelegramBotClient {

    private final TelegramBotApi api;
    private final TelegramBotConfig config;
    private final ObjectMapper objectMapper;

    @Inject
    public TelegramBotClient(@RestClient TelegramBotApi api,
            TelegramBotConfig config,
            ObjectMapper objectMapper) {
        this.api = api;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    private String token() {
        return config.token().orElseThrow(() -> new IllegalStateException(
                "Telegram bot token is not configured. Please set 'quarkus.telegram.token'."));
    }

    public Uni<User> getMe() {
        return api.getMe(token()).map(TelegramResponse::result);
    }

    public Uni<List<Update>> getUpdates(GetUpdatesRequest request) {
        return api.getUpdates(token(), request).map(TelegramResponse::result);
    }

    public Uni<Boolean> setWebhook(SetWebhookRequest request) {
        return api.setWebhook(token(), request).map(TelegramResponse::result);
    }

    public Uni<Boolean> deleteWebhook() {
        return api.deleteWebhook(token()).map(TelegramResponse::result);
    }

    public Uni<Message> sendMessage(SendMessage message) {
        return api.sendMessage(token(), message).map(TelegramResponse::result);
    }

    public Uni<Message> sendMessage(long chatId, String text) {
        return sendMessage(new SendMessage(chatId, text));
    }

    public Uni<Message> sendMessage(String chatId, String text) {
        return sendMessage(new SendMessage(chatId, text));
    }

    public Uni<Boolean> setMyCommands(List<BotCommand> commands) {
        return api.setMyCommands(token(), new SetMyCommandsRequest(commands)).map(TelegramResponse::result);
    }

    public TelegramBotApi api() {
        return api;
    }

    public TelegramBotConfig config() {
        return config;
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }
}
