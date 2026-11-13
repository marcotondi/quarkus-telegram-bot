package io.quarkiverse.telegram.bot.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.CallbackParam;
import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.User;

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
}
