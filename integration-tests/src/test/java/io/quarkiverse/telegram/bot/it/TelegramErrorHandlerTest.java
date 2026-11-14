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
public class TelegramErrorHandlerTest {

    @BeforeEach
    public void resetWireMock() {
        TelegramMockServer.getWireMockServer().resetRequests();
    }

    @Test
    public void testCourtesyMessageSentOnCustomValidationException() throws Exception {
        String updateJson = """
                {
                    "update_id": 9101,
                    "message": {
                        "message_id": 1,
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
                        "text": "/error-validation"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Cortesia: Parametro mancante"))));
    }

    @Test
    public void testScopedOnErrorTakesPrecedenceOverGlobal() throws Exception {
        String updateJson = """
                {
                    "update_id": 9102,
                    "message": {
                        "message_id": 2,
                        "date": 1700000000,
                        "chat": {
                            "id": 1002,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "text": "/error-scoped"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1002")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Scoped: Test Scoped"))));

        // Ensure global was NOT invoked
        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("Global: Test Scoped")));
    }

    @Test
    public void testTelegramExceptionHandlerInterface() throws Exception {
        String updateJson = """
                {
                    "update_id": 9103,
                    "message": {
                        "message_id": 3,
                        "date": 1700000000,
                        "chat": {
                            "id": 1003,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "text": "/error-order"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1003")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("HandlerInterface: Ordine non valido"))));
    }

    @Test
    public void testAsyncUniFailureHandledByOnError() throws Exception {
        String updateJson = """
                {
                    "update_id": 9104,
                    "message": {
                        "message_id": 4,
                        "date": 1700000000,
                        "chat": {
                            "id": 1004,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "text": "/error-async"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1004")))
                .withRequestBody(
                        WireMock.matchingJsonPath("$.text", WireMock.equalTo("AsyncHandled: Async failure occurred"))));
    }

    @Test
    public void testUnhandledExceptionReturns500() {
        String updateJson = """
                {
                    "update_id": 9105,
                    "message": {
                        "message_id": 5,
                        "date": 1700000000,
                        "chat": {
                            "id": 1005,
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
