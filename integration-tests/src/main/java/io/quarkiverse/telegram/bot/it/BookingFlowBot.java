package io.quarkiverse.telegram.bot.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.CallbackParam;
import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.OnState;
import io.quarkiverse.telegram.bot.runtime.api.OnStateMismatch;
import io.quarkiverse.telegram.bot.runtime.api.SessionPayload;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.session.ConversationContext;

@ApplicationScoped
public class BookingFlowBot {

    @SessionPayload
    public record BookingData(String date, Integer guests) {
    }

    @TelegramCommand(value = "/prenota", description = "Avvia una nuova prenotazione")
    public String startBooking(ConversationContext ctx) {
        ctx.transitionTo("WAITING_DATE", new BookingData(null, null));
        return "Per quale data vuoi prenotare? (es. 2026-12-01)";
    }

    @OnState("WAITING_DATE")
    @OnMessage(type = MessageType.TEXT)
    public SendMessage receiveDate(Message msg, ConversationContext ctx) {
        String date = msg.text().trim();
        ctx.transitionTo("WAITING_GUESTS", new BookingData(date, null));

        return SendMessage.builder()
                .chatId(msg.chat().id())
                .text("Data registrata: " + date + ". Quanti ospiti sarete?")
                .replyMarkup(InlineKeyboard.of("2 persone", "guests_2", "4 persone", "guests_4"))
                .build();
    }

    @OnState("WAITING_DATE")
    @OnStateMismatch("WAITING_DATE")
    public String dateMismatch() {
        return "Formato non valido! Invia una data di testo valida.";
    }

    @OnState("WAITING_GUESTS")
    @OnCallbackQuery("guests_*")
    public String confirmGuests(CallbackQuery query, @CallbackParam String num, ConversationContext ctx, BookingData data) {
        int guests = Integer.parseInt(num);
        String bookingDate = data != null ? data.date() : "N/D";
        ctx.complete();
        return "Prenotazione confermata per " + guests + " ospiti il " + bookingDate + "!";
    }

    @TelegramCommand(value = "/annulla", interruptsState = true)
    public String cancel(ConversationContext ctx) {
        if (ctx.currentState().isEmpty()) {
            return "Nessuna operazione in corso da annullare.";
        }
        ctx.complete();
        return "Operazione annullata.";
    }
}
