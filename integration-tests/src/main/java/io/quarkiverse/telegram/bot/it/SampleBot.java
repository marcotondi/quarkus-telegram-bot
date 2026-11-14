package io.quarkiverse.telegram.bot.it;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.telegram.bot.runtime.api.CallbackParam;
import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.api.OnError;
import io.quarkiverse.telegram.bot.runtime.api.OnInlineQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.ChatAction;
import io.quarkiverse.telegram.bot.runtime.model.ChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.model.DeleteMessage;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageReplyMarkup;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.InlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResultArticle;
import io.quarkiverse.telegram.bot.runtime.model.InputTextMessageContent;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendChatAction;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.quarkiverse.telegram.bot.runtime.session.ConversationContext;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class SampleBot {

    @TelegramCommand(value = "/start", description = "Avvia bot")
    public SendMessage onStart(User user, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Ciao " + user.firstName())
                .build();
    }

    @TelegramCommand(value = "/echo")
    public String onEcho(Message msg) {
        return "ECHO: " + msg.text();
    }

    @TelegramCommand(value = "/fail")
    public String onFail() {
        throw new RuntimeException("Simulated handler crash");
    }

    @OnMessage(type = MessageType.PHOTO)
    public String onPhotoMessage(Message message) {
        return "Photo received";
    }

    @OnCallbackQuery("item_*")
    public String onCallback(CallbackQuery query, @CallbackParam String item) {
        return "Selected: " + item;
    }

    @OnCallbackQuery("page_*")
    public EditMessageText onPage(CallbackQuery query, @CallbackParam String page) {
        return EditMessageText.builder()
                .text("Contenuto pagina " + page)
                .replyMarkup(InlineKeyboard.of("Succ ➡️", "page_" + (Integer.parseInt(page) + 1)))
                .build();
    }

    @OnCallbackQuery("alert_*")
    public AnswerCallbackQuery onAlert(@CallbackParam String text) {
        return AnswerCallbackQuery.builder()
                .text("Alert: " + text)
                .showAlert(true)
                .build();
    }

    public static final java.util.concurrent.atomic.AtomicInteger COUNTED_CALLS = new java.util.concurrent.atomic.AtomicInteger();

    @TelegramCommand("/counted")
    public String onCounted() {
        COUNTED_CALLS.incrementAndGet();
        try {
            Thread.sleep(60);
        } catch (InterruptedException ignored) {
        }
        return "Counted";
    }

    @OnMessage(type = MessageType.TEXT)
    public String onTextMessage(Message message) {
        return "Text received: " + message.text();
    }

    public static final java.util.concurrent.atomic.AtomicReference<ChosenInlineResult> LAST_CHOSEN_RESULT = new java.util.concurrent.atomic.AtomicReference<>();

    @OnInlineQuery("search_*")
    public List<InlineQueryResult> onSearchInlineQuery(InlineQuery query, String queryText) {
        return List.of(
                InlineQueryResultArticle.builder()
                        .id("search_1")
                        .title("Search Result for " + queryText)
                        .type("article")
                        .inputMessageContent(new InputTextMessageContent("Found: " + queryText, null, null, null))
                        .build());
    }

    @OnInlineQuery("custom_*")
    public AnswerInlineQuery onCustomInlineQuery(InlineQuery query) {
        return AnswerInlineQuery.builder()
                .results(List.of(
                        InlineQueryResultArticle.builder()
                                .id("custom_1")
                                .title("Custom: " + query.query())
                                .type("article")
                                .inputMessageContent(new InputTextMessageContent("Custom text", null, null, null))
                                .build()))
                .cacheTime(60)
                .isPersonal(true)
                .build();
    }

    @OnChosenInlineResult("*")
    public void onChosenInlineResult(ChosenInlineResult chosen) {
        LAST_CHOSEN_RESULT.set(chosen);
    }

    @Inject
    TelegramBotClient botClient;

    public static class CustomValidationException extends RuntimeException {
        public CustomValidationException(String msg) {
            super(msg);
        }
    }

    public static class ScopedTestException extends RuntimeException {
        public ScopedTestException(String msg) {
            super(msg);
        }
    }

    public static class OrderException extends RuntimeException {
        public OrderException(String msg) {
            super(msg);
        }
    }

    public static class AsyncFailureException extends RuntimeException {
        public AsyncFailureException(String msg) {
            super(msg);
        }
    }

    @TelegramCommand("/error-validation")
    public String onErrorValidation() {
        throw new CustomValidationException("Parametro mancante");
    }

    @OnError(CustomValidationException.class)
    public String handleValidation(CustomValidationException ex) {
        return "Cortesia: " + ex.getMessage();
    }

    @TelegramCommand("/error-scoped")
    public String onErrorScoped() {
        throw new ScopedTestException("Test Scoped");
    }

    @OnError(ScopedTestException.class)
    public String handleScoped(ScopedTestException ex) {
        return "Scoped: " + ex.getMessage();
    }

    @TelegramCommand("/error-order")
    public String onErrorOrder() {
        throw new OrderException("Ordine non valido");
    }

    @TelegramCommand("/error-async")
    public Uni<String> onErrorAsync() {
        return Uni.createFrom().failure(new AsyncFailureException("Async failure occurred"));
    }

    @OnError(AsyncFailureException.class)
    public Uni<Void> handleAsync(AsyncFailureException ex, Update update, ConversationContext ctx) {
        Long chatId = update.message().chat().id();
        return botClient.sendMessage(chatId, "AsyncHandled: " + ex.getMessage()).replaceWithVoid();
    }

    @TelegramCommand("/test-delete")
    public DeleteMessage onDeleteCommand(Chat chat) {
        return new DeleteMessage(chat.id(), 12345L);
    }

    @TelegramCommand("/test-action")
    public SendChatAction onActionCommand(Chat chat) {
        return new SendChatAction(chat.id(), ChatAction.TYPING);
    }

    @OnCallbackQuery("markup_*")
    public EditMessageReplyMarkup onMarkupCallback(Chat chat, CallbackQuery query) {
        return new EditMessageReplyMarkup(chat.id(), 999L, InlineKeyboard.of("Updated", "markup_done"));
    }
}
