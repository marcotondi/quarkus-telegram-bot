package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramSecurityAndInterceptorsTest {

    @BeforeEach
    public void setup() {
        TelegramMockServer.getWireMockServer().resetRequests();
        AuditTrailInterceptor.clear();
    }

    @Test
    public void testAdminAccessGrantedWhenInAdminIds() throws Exception {
        String updateJson = """
                {
                    "update_id": 1101,
                    "message": {
                        "message_id": 1,
                        "date": 1700000000,
                        "chat": {
                            "id": 5001,
                            "type": "private"
                        },
                        "from": {
                            "id": 123456789,
                            "is_bot": false,
                            "first_name": "Admin"
                        },
                        "text": "/admin-only"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("5001")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Welcome Admin 123456789"))));

        assertTrue(AuditTrailInterceptor.EVENTS.contains("BEFORE_10"));
        assertTrue(AuditTrailInterceptor.EVENTS.contains("BEFORE_20"));
        assertTrue(AuditTrailInterceptor.EVENTS.contains("AFTER_10:SUCCESS"));
        assertTrue(AuditTrailInterceptor.EVENTS.contains("AFTER_20"));
    }

    @Test
    public void testAdminAccessDeniedWhenNotInAdminIds() throws Exception {
        String updateJson = """
                {
                    "update_id": 1102,
                    "message": {
                        "message_id": 2,
                        "date": 1700000000,
                        "chat": {
                            "id": 5002,
                            "type": "private"
                        },
                        "from": {
                            "id": 555555,
                            "is_bot": false,
                            "first_name": "Intruder"
                        },
                        "text": "/admin-only"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("5002")))
                .withRequestBody(WireMock.containing("ACCESSO NEGATO: Access denied for user 555555")));

        assertTrue(AuditTrailInterceptor.EVENTS.contains("AFTER_10:TelegramSecurityException"));
    }

    @Test
    public void testAdminSilentAccessDeniedDoesNotSendResponse() throws Exception {
        String updateJson = """
                {
                    "update_id": 1103,
                    "message": {
                        "message_id": 3,
                        "date": 1700000000,
                        "chat": {
                            "id": 5003,
                            "type": "private"
                        },
                        "from": {
                            "id": 555555,
                            "is_bot": false,
                            "first_name": "Intruder"
                        },
                        "text": "/admin-silent"
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

        Thread.sleep(500);
        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("Silent Admin OK")));
    }

    @Test
    public void testVipAccessGrantedByAllowedUsers() throws Exception {
        String updateJson = """
                {
                    "update_id": 1104,
                    "message": {
                        "message_id": 4,
                        "date": 1700000000,
                        "chat": {
                            "id": 5004,
                            "type": "private"
                        },
                        "from": {
                            "id": 7777777,
                            "is_bot": false,
                            "first_name": "VipUser"
                        },
                        "text": "/vip-only"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("5004")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Welcome VIP 7777777"))));
    }

    @Test
    public void testVipAccessGrantedByCustomRoleResolver() throws Exception {
        String updateJson = """
                {
                    "update_id": 1105,
                    "message": {
                        "message_id": 5,
                        "date": 1700000000,
                        "chat": {
                            "id": 5005,
                            "type": "private"
                        },
                        "from": {
                            "id": 8888888,
                            "is_bot": false,
                            "first_name": "RoleVipUser"
                        },
                        "text": "/vip-only"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("5005")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Welcome VIP 8888888"))));
    }

    @Test
    public void testChatTypeFilterPrivateMatchesPrivateOnly() throws Exception {
        String updateJson = """
                {
                    "update_id": 1106,
                    "message": {
                        "message_id": 6,
                        "date": 1700000000,
                        "chat": {
                            "id": 6001,
                            "type": "private"
                        },
                        "from": {
                            "id": 12345,
                            "is_bot": false,
                            "first_name": "User"
                        },
                        "text": "/chat-filter"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("6001")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Response from PRIVATE chat"))));
    }

    @Test
    public void testChatTypeFilterGroupMatchesGroupOnly() throws Exception {
        String updateJson = """
                {
                    "update_id": 1107,
                    "message": {
                        "message_id": 7,
                        "date": 1700000000,
                        "chat": {
                            "id": -1006002,
                            "type": "group"
                        },
                        "from": {
                            "id": 12345,
                            "is_bot": false,
                            "first_name": "User"
                        },
                        "text": "/chat-filter"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("-1006002")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Response from GROUP chat"))));
    }

    @Test
    public void testChatTypeFilterSupergroupMatchesGroupOnly() throws Exception {
        String updateJson = """
                {
                    "update_id": 1108,
                    "message": {
                        "message_id": 8,
                        "date": 1700000000,
                        "chat": {
                            "id": -1006003,
                            "type": "supergroup"
                        },
                        "from": {
                            "id": 12345,
                            "is_bot": false,
                            "first_name": "User"
                        },
                        "text": "/chat-filter"
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
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("-1006003")))
                .withRequestBody(WireMock.matchingJsonPath("$.text", WireMock.equalTo("Response from GROUP chat"))));
    }

    @Test
    public void testChatTypeMismatchDoesNotMatch() throws Exception {
        String updateJson = """
                {
                    "update_id": 1109,
                    "message": {
                        "message_id": 9,
                        "date": 1700000000,
                        "chat": {
                            "id": 6004,
                            "type": "private"
                        },
                        "from": {
                            "id": 12345,
                            "is_bot": false,
                            "first_name": "User"
                        },
                        "text": "/channel-only"
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

        Thread.sleep(500);
        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("Response from CHANNEL")));
    }

    @Test
    public void testBeforeUpdateShortCircuit() throws Exception {
        String updateJson = """
                {
                    "update_id": 1110,
                    "message": {
                        "message_id": 10,
                        "date": 1700000000,
                        "chat": {
                            "id": 6005,
                            "type": "private"
                        },
                        "from": {
                            "id": 99999,
                            "is_bot": false,
                            "first_name": "Bad",
                            "username": "banned_user"
                        },
                        "text": "/chat-filter"
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

        Thread.sleep(500);
        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("Response from PRIVATE chat")));

        assertTrue(AuditTrailInterceptor.EVENTS.contains("BANNED_BLOCKED"));
        assertFalse(AuditTrailInterceptor.EVENTS.contains("BEFORE_20"));
        assertTrue(AuditTrailInterceptor.EVENTS.contains("AFTER_10:SUCCESS"));
        assertTrue(AuditTrailInterceptor.EVENTS.contains("AFTER_20"));
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
