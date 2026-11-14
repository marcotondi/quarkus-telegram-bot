package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramFsmTest {

    @BeforeEach
    public void resetWireMock() {
        TelegramMockServer.getWireMockServer().resetRequests();
    }

    @Test
    public void testFullBookingConversationFlow() throws Exception {
        // Step 1: Start booking with /prenota
        String startJson = """
                {
                    "update_id": 101,
                    "message": {
                        "message_id": 1,
                        "date": 1700000000,
                        "chat": { "id": 5001, "type": "private" },
                        "from": { "id": 9001, "is_bot": false, "first_name": "Alice" },
                        "text": "/prenota"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(startJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Per quale data vuoi prenotare?");

        // Step 2: Send mismatched input while in WAITING_DATE (a callback query instead of text)
        String mismatchJson = """
                {
                    "update_id": 102,
                    "callback_query": {
                        "id": "cb_mismatch",
                        "from": { "id": 9001, "is_bot": false, "first_name": "Alice" },
                        "message": {
                            "message_id": 1,
                            "date": 1700000000,
                            "chat": { "id": 5001, "type": "private" }
                        },
                        "data": "some_random_click"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(mismatchJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Formato non valido!");

        // Step 3: Send valid date in text message
        String dateJson = """
                {
                    "update_id": 103,
                    "message": {
                        "message_id": 2,
                        "date": 1700000001,
                        "chat": { "id": 5001, "type": "private" },
                        "from": { "id": 9001, "is_bot": false, "first_name": "Alice" },
                        "text": "2026-12-25"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(dateJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Data registrata: 2026-12-25. Quanti ospiti sarete?");

        // Step 4: Confirm guests via callback query
        String guestsJson = """
                {
                    "update_id": 104,
                    "callback_query": {
                        "id": "cb_guests",
                        "from": { "id": 9001, "is_bot": false, "first_name": "Alice" },
                        "message": {
                            "message_id": 2,
                            "date": 1700000001,
                            "chat": { "id": 5001, "type": "private" }
                        },
                        "data": "guests_4"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(guestsJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Prenotazione confermata per 4 ospiti il 2026-12-25!");
    }

    @Test
    public void testCancelInterruptsState() throws Exception {
        // Step 1: Start booking
        String startJson = """
                {
                    "update_id": 201,
                    "message": {
                        "message_id": 11,
                        "date": 1700000000,
                        "chat": { "id": 5002, "type": "private" },
                        "from": { "id": 9002, "is_bot": false, "first_name": "Bob" },
                        "text": "/prenota"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(startJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Per quale data vuoi prenotare?");

        // Step 2: Cancel with /annulla (interruptsState = true)
        String cancelJson = """
                {
                    "update_id": 202,
                    "message": {
                        "message_id": 12,
                        "date": 1700000001,
                        "chat": { "id": 5002, "type": "private" },
                        "from": { "id": 9002, "is_bot": false, "first_name": "Bob" },
                        "text": "/annulla"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(cancelJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Operazione annullata.");

        // Step 3: Send /annulla again when no state is active
        String cancelAgainJson = """
                {
                    "update_id": 203,
                    "message": {
                        "message_id": 13,
                        "date": 1700000002,
                        "chat": { "id": 5002, "type": "private" },
                        "from": { "id": 9002, "is_bot": false, "first_name": "Bob" },
                        "text": "/annulla"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(cancelAgainJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Nessuna operazione in corso da annullare.");
    }

    @Test
    public void testInterruptsStateClearsStateAutomatically() throws Exception {
        // Step 1: Start booking (enters WAITING_DATE)
        String startJson = """
                {
                    "update_id": 251,
                    "message": {
                        "message_id": 21,
                        "date": 1700000000,
                        "chat": { "id": 5005, "type": "private" },
                        "from": { "id": 9005, "is_bot": false, "first_name": "Charlie" },
                        "text": "/prenota"
                    }
                }
                """;
        given().contentType(ContentType.JSON).body(startJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Per quale data vuoi prenotare?");

        // Step 2: Send /reset (interruptsState = true, does NOT call ctx.complete() manually)
        String resetJson = """
                {
                    "update_id": 252,
                    "message": {
                        "message_id": 22,
                        "date": 1700000001,
                        "chat": { "id": 5005, "type": "private" },
                        "from": { "id": 9005, "is_bot": false, "first_name": "Charlie" },
                        "text": "/reset"
                    }
                }
                """;
        given().contentType(ContentType.JSON).body(resetJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Reset eseguito.");

        // Step 3: Send /annulla - since state was cleared by /reset, it should report no active operation!
        String checkJson = """
                {
                    "update_id": 253,
                    "message": {
                        "message_id": 23,
                        "date": 1700000002,
                        "chat": { "id": 5005, "type": "private" },
                        "from": { "id": 9005, "is_bot": false, "first_name": "Charlie" },
                        "text": "/annulla"
                    }
                }
                """;
        given().contentType(ContentType.JSON).body(checkJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Nessuna operazione in corso da annullare.");
    }

    @Test
    public void testFsmRollbackOnDeliveryFailure() throws Exception {
        // Step 1: Start booking
        String startJson = """
                {
                    "update_id": 301,
                    "message": {
                        "message_id": 30,
                        "date": 1700000000,
                        "chat": { "id": 5003, "type": "private" },
                        "from": { "id": 9003, "is_bot": false, "first_name": "RollbackUser" },
                        "text": "/prenota"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(startJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Per quale data vuoi prenotare?");

        // Step 2: Stub WireMock to fail on "CRASH_DATE"
        TelegramMockServer.getWireMockServer().stubFor(
                WireMock.post(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("CRASH_DATE"))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: network error\"}")));

        String crashDateJson = """
                {
                    "update_id": 302,
                    "message": {
                        "message_id": 31,
                        "date": 1700000001,
                        "chat": { "id": 5003, "type": "private" },
                        "from": { "id": 9003, "is_bot": false, "first_name": "RollbackUser" },
                        "text": "CRASH_DATE"
                    }
                }
                """;

        // Webhook must fail with 500 and NOT save the transition to WAITING_GUESTS
        given().contentType(ContentType.JSON).body(crashDateJson)
                .when().post("/telegram/webhook")
                .then().statusCode(500);

        // Step 3: Send valid date now. Since state was rolled back / not saved, it is still WAITING_DATE!
        String validDateJson = """
                {
                    "update_id": 303,
                    "message": {
                        "message_id": 32,
                        "date": 1700000002,
                        "chat": { "id": 5003, "type": "private" },
                        "from": { "id": 9003, "is_bot": false, "first_name": "RollbackUser" },
                        "text": "2026-12-31"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(validDateJson)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyMessageSent("Data registrata: 2026-12-31. Quanti ospiti sarete?");
    }

    private void verifyMessageSent(String expectedSubtext) throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        AssertionError lastErr = null;
        while (System.currentTimeMillis() < end) {
            try {
                TelegramMockServer.getWireMockServer().verify(
                        WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                                .withRequestBody(WireMock.containing(expectedSubtext)));
                return;
            } catch (AssertionError e) {
                lastErr = e;
                Thread.sleep(100);
            }
        }
        if (lastErr != null) {
            throw lastErr;
        }
    }
}
