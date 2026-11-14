package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

@QuarkusTest
@TestProfile(RateLimitTestProfile.class)
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramRateLimitTest {

    @BeforeEach
    public void setup() {
        TelegramMockServer.getWireMockServer().resetRequests();
    }

    @Test
    public void testSecondUpdateFromSameUserIsThrottled() throws Exception {
        postUpdate(2001, 6001, 777001, "/start");
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Ciao Mario"))));

        postUpdate(2002, 6001, 777001, "/start");
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Rate limit superato, attendi."))));

        // Third update in the same window: throttled silently, no second rejection message.
        postUpdate(2003, 6001, 777001, "/start");
        Thread.sleep(500);
        TelegramMockServer.getWireMockServer().verify(1,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(
                                WireMock.matchingJsonPath("$.text", WireMock.equalTo("Rate limit superato, attendi."))));
    }

    @Test
    public void testOtherUserIsNotThrottled() throws Exception {
        postUpdate(2101, 6101, 777002, "/start");
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Ciao Mario"))));

        postUpdate(2102, 6102, 777003, "/start");
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("6102"))));
    }

    private void postUpdate(long updateId, long chatId, long userId, String text) {
        String updateJson = """
                {
                    "update_id": %d,
                    "message": {
                        "message_id": 1,
                        "date": 1700000000,
                        "chat": {"id": %d, "type": "private"},
                        "from": {"id": %d, "is_bot": false, "first_name": "Mario"},
                        "text": "%s"
                    }
                }
                """.formatted(updateId, chatId, userId, text);

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
        fail(lastErr);
    }
}
