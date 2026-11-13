package io.quarkiverse.telegram.bot.runtime.client;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.BotCommand;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageResult;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.GetUpdatesRequest;
import io.quarkiverse.telegram.bot.runtime.model.InputFile;
import io.quarkiverse.telegram.bot.runtime.model.InputMedia;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.ReplyMarkup;
import io.quarkiverse.telegram.bot.runtime.model.SendAudioJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendDocumentJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendMediaGroupJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.SendPhotoJsonRequest;
import io.quarkiverse.telegram.bot.runtime.model.SetMyCommandsRequest;
import io.quarkiverse.telegram.bot.runtime.model.SetWebhookRequest;
import io.quarkiverse.telegram.bot.runtime.model.TelegramResponse;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.smallrye.mutiny.Uni;
import io.vertx.core.buffer.Buffer;

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

    private <T> Uni<T> extractResult(Uni<TelegramResponse<T>> uni) {
        return uni.map(resp -> {
            if (resp == null) {
                throw new TelegramApiException(null, "Null response from Telegram API");
            }
            if (!resp.ok()) {
                throw new TelegramApiException(resp.errorCode(), resp.description());
            }
            return resp.result();
        });
    }

    private <T> Uni<T> extractMultipartResult(Uni<TelegramResponse<T>> uni) {
        return extractResult(uni.ifNoItem().after(config.client().uploadTimeout()).fail());
    }

    public Uni<User> getMe() {
        return extractResult(api.getMe(token()));
    }

    public Uni<List<Update>> getUpdates(GetUpdatesRequest request) {
        return extractResult(api.getUpdates(token(), request));
    }

    public Uni<Boolean> setWebhook(SetWebhookRequest request) {
        return extractResult(api.setWebhook(token(), request));
    }

    public Uni<Boolean> deleteWebhook() {
        return extractResult(api.deleteWebhook(token()));
    }

    public Uni<Message> sendMessage(SendMessage message) {
        return extractResult(api.sendMessage(token(), message));
    }

    public Uni<Message> sendMessage(long chatId, String text) {
        return sendMessage(new SendMessage(chatId, text));
    }

    public Uni<Message> sendMessage(String chatId, String text) {
        return sendMessage(new SendMessage(chatId, text));
    }

    // --- editMessageText ---

    public Uni<EditMessageResult> editMessageText(EditMessageText request) {
        return extractResult(api.editMessageText(token(), request));
    }

    public Uni<Message> editChatMessageText(EditMessageText request) {
        return editMessageText(request).map(EditMessageResult::asMessage);
    }

    public Uni<Message> editMessageText(long chatId, long messageId, String text) {
        return editChatMessageText(new EditMessageText(chatId, messageId, text, null));
    }

    public Uni<Message> editMessageText(long chatId, long messageId, String text, ReplyMarkup replyMarkup) {
        return editChatMessageText(new EditMessageText(chatId, messageId, text, replyMarkup));
    }

    // --- answerCallbackQuery ---

    public Uni<Boolean> answerCallbackQuery(AnswerCallbackQuery request) {
        return extractResult(api.answerCallbackQuery(token(), request));
    }

    public Uni<Boolean> answerCallbackQuery(String callbackQueryId) {
        return answerCallbackQuery(new AnswerCallbackQuery(callbackQueryId));
    }

    public Uni<Boolean> answerCallbackQuery(String callbackQueryId, String text) {
        return answerCallbackQuery(new AnswerCallbackQuery(callbackQueryId, text));
    }

    public Uni<Boolean> answerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        return answerCallbackQuery(new AnswerCallbackQuery(callbackQueryId, text, showAlert));
    }

    public Uni<Boolean> setMyCommands(List<BotCommand> commands) {
        return extractResult(api.setMyCommands(token(), new SetMyCommandsRequest(commands)));
    }

    // --- sendPhoto ---

    public Uni<Message> sendPhoto(long chatId, InputFile photo) {
        return sendPhoto(chatId, photo, null, null);
    }

    public Uni<Message> sendPhoto(long chatId, InputFile photo, String caption) {
        return sendPhoto(chatId, photo, caption, null);
    }

    public Uni<Message> sendPhoto(long chatId, InputFile photo, String caption, ReplyMarkup markup) {
        return switch (photo) {
            case InputFile.FileId(var id) -> extractResult(api.sendPhotoJson(
                    token(), new SendPhotoJsonRequest(chatId, id, caption, markup)));

            case InputFile.Url(var url) -> extractResult(api.sendPhotoJson(
                    token(), new SendPhotoJsonRequest(chatId, url, caption, markup)));

            case InputFile.BinaryPath(var path, var fileName, var contentType) -> {
                ClientMultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("photo", fileName, path.toAbsolutePath().toString(), contentType);
                yield extractMultipartResult(api.sendPhotoMultipart(token(), form));
            }

            case InputFile.BinaryBytes(var bytes, var fileName, var contentType) -> {
                ClientMultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("photo", fileName, Buffer.buffer(bytes), contentType);
                yield extractMultipartResult(api.sendPhotoMultipart(token(), form));
            }
        };
    }

    // --- sendDocument ---

    public Uni<Message> sendDocument(long chatId, InputFile document) {
        return sendDocument(chatId, document, null, null);
    }

    public Uni<Message> sendDocument(long chatId, InputFile document, String caption) {
        return sendDocument(chatId, document, caption, null);
    }

    public Uni<Message> sendDocument(long chatId, InputFile document, String caption, ReplyMarkup markup) {
        return switch (document) {
            case InputFile.FileId(var id) -> extractResult(api.sendDocumentJson(
                    token(), new SendDocumentJsonRequest(chatId, id, caption, markup)));

            case InputFile.Url(var url) -> extractResult(api.sendDocumentJson(
                    token(), new SendDocumentJsonRequest(chatId, url, caption, markup)));

            case InputFile.BinaryPath(var path, var fileName, var contentType) -> {
                ClientMultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("document", fileName, path.toAbsolutePath().toString(), contentType);
                yield extractMultipartResult(api.sendDocumentMultipart(token(), form));
            }

            case InputFile.BinaryBytes(var bytes, var fileName, var contentType) -> {
                ClientMultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("document", fileName, Buffer.buffer(bytes), contentType);
                yield extractMultipartResult(api.sendDocumentMultipart(token(), form));
            }
        };
    }

    // --- sendAudio ---

    public Uni<Message> sendAudio(long chatId, InputFile audio) {
        return sendAudio(chatId, audio, null, null);
    }

    public Uni<Message> sendAudio(long chatId, InputFile audio, String caption) {
        return sendAudio(chatId, audio, caption, null);
    }

    public Uni<Message> sendAudio(long chatId, InputFile audio, String caption, ReplyMarkup markup) {
        return switch (audio) {
            case InputFile.FileId(var id) -> extractResult(api.sendAudioJson(
                    token(), new SendAudioJsonRequest(chatId, id, caption, markup)));

            case InputFile.Url(var url) -> extractResult(api.sendAudioJson(
                    token(), new SendAudioJsonRequest(chatId, url, caption, markup)));

            case InputFile.BinaryPath(var path, var fileName, var contentType) -> {
                ClientMultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("audio", fileName, path.toAbsolutePath().toString(), contentType);
                yield extractMultipartResult(api.sendAudioMultipart(token(), form));
            }

            case InputFile.BinaryBytes(var bytes, var fileName, var contentType) -> {
                ClientMultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("audio", fileName, Buffer.buffer(bytes), contentType);
                yield extractMultipartResult(api.sendAudioMultipart(token(), form));
            }
        };
    }

    // --- sendMediaGroup ---

    public Uni<List<Message>> sendMediaGroup(long chatId, List<? extends InputMedia> media) {
        return sendMediaGroup(chatId, media, null);
    }

    public Uni<List<Message>> sendMediaGroup(long chatId, List<? extends InputMedia> media,
            Map<String, InputFile> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return extractResult(api.sendMediaGroupJson(token(), new SendMediaGroupJsonRequest(chatId, media)));
        }

        try {
            ClientMultipartForm form = ClientMultipartForm.create()
                    .attribute("chat_id", String.valueOf(chatId), "text/plain")
                    .attribute("media", objectMapper.writeValueAsString(media), "application/json");

            for (Map.Entry<String, InputFile> entry : attachments.entrySet()) {
                String fieldName = entry.getKey();
                InputFile file = entry.getValue();
                switch (file) {
                    case InputFile.BinaryPath(var path, var fileName, var contentType) ->
                        form.binaryFileUpload(fieldName, fileName, path.toAbsolutePath().toString(), contentType);
                    case InputFile.BinaryBytes(var bytes, var fileName, var contentType) ->
                        form.binaryFileUpload(fieldName, fileName, Buffer.buffer(bytes), contentType);
                    case InputFile.FileId(var id) ->
                        form.attribute(fieldName, id, "text/plain");
                    case InputFile.Url(var url) ->
                        form.attribute(fieldName, url, "text/plain");
                }
            }

            return extractMultipartResult(api.sendMediaGroupMultipart(token(), form));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Impossibile serializzare media group", e);
        }
    }

    private ClientMultipartForm baseMultipart(long chatId, String caption, ReplyMarkup markup) {
        ClientMultipartForm form = ClientMultipartForm.create()
                .attribute("chat_id", String.valueOf(chatId), "text/plain");

        if (caption != null) {
            form.attribute("caption", caption, "text/plain");
        }
        if (markup != null) {
            try {
                form.attribute("reply_markup", objectMapper.writeValueAsString(markup), "application/json");
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Impossibile serializzare reply_markup", e);
            }
        }
        return form;
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
