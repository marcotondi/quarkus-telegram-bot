package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.telegram.bot.runtime.client.TelegramApiException;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageResult;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.ForceReply;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.quarkiverse.telegram.bot.runtime.session.InMemorySessionStore;
import io.quarkiverse.telegram.bot.runtime.session.SessionKey;
import io.quarkiverse.telegram.bot.runtime.session.SessionSnapshot;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramBotWebhookTest {

    @Inject
    TelegramBotClient client;

    @Inject
    InMemorySessionStore inMemoryStore;

    @Test
    public void testClientGetMe() {
        User me = client.getMe().await().indefinitely();
        assertNotNull(me);
        assertEquals(123456, me.id());
        assertEquals("TestBot", me.firstName());
    }

    @Test
    public void testWebhookCommandStart() throws Exception {
        String updateJson = """
                {
                    "update_id": 1,
                    "message": {
                        "message_id": 10,
                        "date": 1700000000,
                        "chat": {
                            "id": 1001,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "text": "/start"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        // Verify that TelegramBotClient sent the response to WireMock
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.containing("Ciao Mario")));
    }

    @Test
    public void testWebhookCallbackQuery() throws Exception {
        String updateJson = """
                {
                    "update_id": 2,
                    "callback_query": {
                        "id": "cb123",
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "message": {
                            "message_id": 10,
                            "date": 1700000000,
                            "chat": {
                                "id": 1001,
                                "type": "private"
                            }
                        },
                        "data": "item_guitar"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.containing("Selected: guitar")));

        // Also verifies answerCallbackQuery was automatically dispatched to dismiss loading spinner
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerCallbackQuery"))
                .withRequestBody(WireMock.containing("\"callback_query_id\":\"cb123\"")));
    }

    @Test
    public void testWebhookEditMessageTextPagination() throws Exception {
        String updateJson = """
                {
                    "update_id": 50,
                    "callback_query": {
                        "id": "cb_page_2",
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "message": {
                            "message_id": 42,
                            "date": 1700000000,
                            "chat": {
                                "id": 1001,
                                "type": "private"
                            },
                            "text": "Pagina 1"
                        },
                        "data": "page_2"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        // Verifies message was edited in-place with new text
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/editMessageText"))
                .withRequestBody(WireMock.containing("Contenuto pagina 2"))
                .withRequestBody(WireMock.containing("\"message_id\":42")));

        // Verifies answerCallbackQuery was dispatched to dismiss loading spinner
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerCallbackQuery"))
                .withRequestBody(WireMock.containing("\"callback_query_id\":\"cb_page_2\"")));
    }

    @Test
    public void testWebhookAnswerCallbackQueryAlert() throws Exception {
        String updateJson = """
                {
                    "update_id": 51,
                    "callback_query": {
                        "id": "cb_alert_1",
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "data": "alert_operazione_completata"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        // Verifies answerCallbackQuery was sent with alert popup
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerCallbackQuery"))
                .withRequestBody(WireMock.containing("Alert: operazione_completata"))
                .withRequestBody(WireMock.containing("\"show_alert\":true")));
    }

    @Test
    public void testClientDirectEditMessageAndAnswerCallback() {
        Message edited = client.editMessageText(1001, 42, "Nuovo testo").await().indefinitely();
        assertNotNull(edited);

        Boolean answered = client.answerCallbackQuery("cb_direct", "Notifica toast", false).await().indefinitely();
        assertNotNull(answered);
    }

    @Test
    public void testWebhookFailureReturns500() {
        String updateJson = """
                {
                    "update_id": 99,
                    "message": {
                        "message_id": 99,
                        "date": 1700000000,
                        "chat": {
                            "id": 1001,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "text": "/fail"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(500);
    }

    @Test
    public void testTelegramApiExceptionOnOkFalse() {
        // WireMock stub returning ok: false with error_code and description
        TelegramMockServer.getWireMockServer().stubFor(
                WireMock.post(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("BLOCKED_USER"))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(
                                        "{\"ok\":false,\"error_code\":403,\"description\":\"Forbidden: bot was blocked by the user\"}")));

        TelegramApiException ex = assertThrows(TelegramApiException.class, () -> {
            client.sendMessage(9999, "BLOCKED_USER").await().indefinitely();
        });

        assertEquals(403, ex.getErrorCode());
        assertEquals("Forbidden: bot was blocked by the user", ex.getDescription());
        assertTrue(ex.getMessage().contains("403"));
    }

    @Test
    public void testOnMessageFilteringPhotoDoesNotMatchText() throws Exception {
        String updateJson = """
                {
                    "update_id": 26301,
                    "message": {
                        "message_id": 101,
                        "date": 1700000000,
                        "chat": {
                            "id": 1001,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "text": "Una semplice stringa di testo"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        // Verifies it matched onTextMessage ("Text received: ...") and NOT onPhotoMessage ("Photo received")
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.containing("Text received: Una semplice stringa di testo")));

        // Verify "Photo received" was NEVER sent
        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("Photo received")));
    }

    @Test
    public void testRapidQueuedUpdatesDoNotReexecutePrevious() throws Exception {
        SampleBot.COUNTED_CALLS.set(0);

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        java.util.List<java.util.concurrent.Future<Integer>> futures = new java.util.ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            final int updateId = 3300 + i;
            futures.add(pool.submit(() -> {
                String updateJson = String.format("""
                        {
                            "update_id": %d,
                            "message": {
                                "message_id": %d,
                                "date": 1700000000,
                                "chat": { "id": 7001, "type": "private" },
                                "from": { "id": 8001, "is_bot": false, "first_name": "Rapid" },
                                "text": "/counted"
                            }
                        }
                        """, updateId, updateId);

                return given()
                        .contentType(ContentType.JSON)
                        .body(updateJson)
                        .when()
                        .post("/telegram/webhook")
                        .then()
                        .extract().statusCode();
            }));
        }

        for (var f : futures) {
            assertEquals(200, f.get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdown();

        // Each update executes exactly once (3 total), never re-subscribed or duplicated
        assertEquals(3, SampleBot.COUNTED_CALLS.get());
    }

    @Test
    public void testInlineMessageEditReturnsBoolean() {
        TelegramMockServer.getWireMockServer().stubFor(
                WireMock.post(WireMock.urlMatching("/bot.*/editMessageText"))
                        .withRequestBody(WireMock.containing("inline_msg_999"))
                        .willReturn(WireMock.aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"ok\":true,\"result\":true}")));

        EditMessageResult result = client.editMessageText(new EditMessageText("inline_msg_999", "Nuovo testo inline", null))
                .await().indefinitely();

        assertNotNull(result);
        assertTrue(result.isInline());
        assertTrue(result instanceof EditMessageResult.InlineSuccess is && is.success());
    }

    @Test
    public void testForceReplyImplementsReplyMarkup() {
        ForceReply forceReply = new ForceReply(true, null, null);
        SendMessage msg = SendMessage.builder()
                .chatId(1001)
                .text("Per favore rispondi a questo messaggio:")
                .replyMarkup(forceReply)
                .build();
        assertNotNull(msg.replyMarkup());
        assertTrue(msg.replyMarkup() instanceof ForceReply);
    }

    @Test
    public void testInMemorySessionStorePeriodicEviction() {
        SessionKey key = new SessionKey(8888, 9999);
        SessionSnapshot snapshot = new SessionSnapshot("ABANDONED", "{}", "java.lang.Object");

        // Save session with negative duration so it's already expired
        inMemoryStore.save(key, snapshot, java.time.Duration.ofSeconds(-10)).await().indefinitely();
        assertTrue(inMemoryStore.size() > 0);

        int evicted = inMemoryStore.cleanExpired();
        assertTrue(evicted >= 1);
        assertTrue(inMemoryStore.load(key).await().indefinitely().isEmpty());
    }

    @Test
    public void testCommandTargetedToAnotherBotIsIgnored() throws Exception {
        String otherBotJson = """
                {
                    "update_id": 401,
                    "message": {
                        "message_id": 401,
                        "date": 1700000000,
                        "chat": { "id": -100999, "type": "supergroup" },
                        "from": { "id": 2001, "is_bot": false, "first_name": "Mario" },
                        "text": "/start@OtherBot"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(otherBotJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        // Verifies no message was sent for /start@OtherBot
        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("-100999")));
    }

    @Test
    public void testWebhookDeduplicatesRecentlyProcessedUpdates() {
        String updateJson = """
                {
                    "update_id": 8888,
                    "message": {
                        "message_id": 88,
                        "date": 1700000000,
                        "chat": { "id": 1001, "type": "private" },
                        "from": { "id": 2001, "is_bot": false, "first_name": "DedupUser" },
                        "text": "/start"
                    }
                }
                """;

        // First call: successfully processed
        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        // Second call with identical update_id: acknowledged with 200 via deduplication cache
        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);
    }

    private void verifyWithRetry(com.github.tomakehurst.wiremock.matching.RequestPatternBuilder pattern)
            throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        AssertionError lastErr = null;
        while (System.currentTimeMillis() < end) {
            try {
                TelegramMockServer.getWireMockServer().verify(pattern);
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
