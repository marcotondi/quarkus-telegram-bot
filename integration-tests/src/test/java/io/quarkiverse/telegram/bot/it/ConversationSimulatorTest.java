package io.quarkiverse.telegram.bot.it;

import java.util.regex.Pattern;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.test.TelegramBotTestResource;
import io.quarkiverse.telegram.bot.test.TelegramBotTestSupport;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(TelegramBotTestResource.class)
public class ConversationSimulatorTest {

    @Inject
    TelegramBotTestSupport botTester;

    @Test
    public void testFluentCommandAndButtonEditFlow() {
        // Matches the fluent DX requested in Roadmap Point 66:
        // botTester.user(12345L)
        //     .sendsCommand("/start")
        //     .assertBotReplied("Benvenuto!")
        //     .clicksButton("p2_film")
        //     .assertMessageEdited();

        botTester.user(12345L)
                .sendsCommand("/start")
                .assertBotReplied("Ciao User12345")
                .clicksButton("page_1")
                .assertMessageEdited("Contenuto pagina 1");
    }

    @Test
    public void testFullBookingConversationFlowWithDx() {
        // Test multi-step FSM conversation with fluent DX
        botTester.user(9001L, 5001L)
                .sendsCommand("/prenota")
                .assertBotReplied("Per quale data vuoi prenotare?")
                .sendsMessage("2026-12-25")
                .assertBotReplied("Data registrata: 2026-12-25. Quanti ospiti sarete?")
                .assertReplyMarkupContains("4 persone")
                .assertInlineButton("4 persone", "guests_4")
                .clicksButton("guests_4")
                .assertBotReplied("Prenotazione confermata per 4 ospiti il 2026-12-25!");
    }

    @Test
    public void testStaticEntrypointAndAssertions() {
        TelegramBotTestSupport.simulate(8888L)
                .sendsCommand("/echo Hello DX")
                .assertBotRepliedExact("ECHO: /echo Hello DX")
                .assertBotRepliedMatching(Pattern.compile(".*Hello DX.*"));
    }

    @Test
    public void testCallbackAlertAnswered() {
        botTester.user(3333L)
                .clicksButton("alert_warning")
                .assertCallbackAnswered("Alert: warning");
    }

    @Test
    public void testInlineQuerySimulation() {
        botTester.user(4444L)
                .sendsInlineQuery("search_quarkus")
                .assertInlineQueryAnswered(1);
    }

    @Test
    public void testNoMessageSentOnUnknownTarget() {
        botTester.user(5555L, -100999L)
                .chat(-100999L, "supergroup")
                .sendsCommand("/start@OtherBot")
                .assertNoMessageSent();
    }
}
