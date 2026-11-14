package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

@QuarkusTest
@TestProfile(TelegramWebhookSecretTokenTest.Profile.class)
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramWebhookSecretTokenTest {

    private static final String SECRET_TOKEN = "test-secret-token-12345";

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.telegram.mode", "WEBHOOK",
                    "quarkus.telegram.webhook.require-secret-token", "true",
                    "quarkus.telegram.webhook.secret-token", SECRET_TOKEN);
        }
    }

    private static final String VALID_UPDATE = """
            {
                "update_id": 9999,
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
                        "first_name": "TestUser"
                    },
                    "text": "/start"
                }
            }
            """;

    @Test
    public void testMissingSecretTokenReturns403() {
        given()
                .contentType(ContentType.JSON)
                .body(VALID_UPDATE)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(403);
    }

    @Test
    public void testWrongSecretTokenReturns403() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret-token")
                .body(VALID_UPDATE)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(403);
    }

    @Test
    public void testValidSecretTokenReturns200() {
        given()
                .contentType(ContentType.JSON)
                .header("X-Telegram-Bot-Api-Secret-Token", SECRET_TOKEN)
                .body(VALID_UPDATE)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);
    }
}
