package io.quarkiverse.telegram.bot.it;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.test.TelegramBotTestResource;
import io.quarkiverse.telegram.bot.test.TelegramBotTestSupport;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(TelegramBotTestResource.class)
public class TelegramConversationAdvancedFlowTest {

    @Inject
    TelegramBotTestSupport botTester;

    @Test
    public void testCancelInterruptsActiveState() {
        botTester.user(7101L)
                .sendsCommand("/prenota")
                .assertBotReplied("Per quale data vuoi prenotare?")
                .sendsCommand("/annulla")
                .assertBotReplied("Operazione annullata.")
                // Now state is completed; sending a random date falls back to default handler instead of advancing booking state
                .sendsMessage("2026-12-25")
                .assertBotReplied("Text received: 2026-12-25");
    }

    @Test
    public void testCancelWhenNoConversationActive() {
        botTester.user(7102L)
                .sendsCommand("/annulla")
                .assertBotReplied("Nessuna operazione in corso da annullare.");
    }

    @Test
    public void testStateMismatchRecoveryFlow() {
        botTester.user(7103L)
                .sendsCommand("/prenota")
                .assertBotReplied("Per quale data vuoi prenotare?")
                // Send unexpected button click instead of text date
                .clicksButton("random_button_click")
                .assertBotReplied("Formato non valido! Invia una data di testo valida.")
                // User recovers by sending valid date
                .sendsMessage("2026-12-31")
                .assertBotReplied("Data registrata: 2026-12-31. Quanti ospiti sarete?")
                .clicksButton("guests_2")
                .assertBotReplied("Prenotazione confermata per 2 ospiti il 2026-12-31!");
    }

    @Test
    public void testConcurrentConversationsBetweenDifferentUsers() {
        // Alice starts booking
        botTester.user(8101L)
                .sendsCommand("/prenota")
                .assertBotReplied("Per quale data vuoi prenotare?");

        // Bob says hello
        botTester.user(8102L)
                .sendsCommand("/start")
                .assertBotReplied("Ciao User8102");

        // Alice advances to WAITING_GUESTS
        botTester.user(8101L)
                .sendsMessage("2027-01-01")
                .assertBotReplied("Data registrata: 2027-01-01. Quanti ospiti sarete?");

        // Bob starts booking
        botTester.user(8102L)
                .sendsCommand("/prenota")
                .assertBotReplied("Per quale data vuoi prenotare?");

        // Alice confirms guests
        botTester.user(8101L)
                .clicksButton("guests_4")
                .assertBotReplied("Prenotazione confermata per 4 ospiti il 2027-01-01!");

        // Bob cancels
        botTester.user(8102L)
                .sendsCommand("/annulla")
                .assertBotReplied("Operazione annullata.");
    }
}
