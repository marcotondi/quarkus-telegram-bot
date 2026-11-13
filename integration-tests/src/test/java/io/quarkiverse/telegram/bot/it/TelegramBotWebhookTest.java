package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramBotWebhookTest {

    @Inject
    TelegramBotClient client;

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
