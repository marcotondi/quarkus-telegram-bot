package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;

@QuarkusTest
@TestProfile(TelegramCommandWithoutBotUsernameTest.Profile.class)
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramCommandWithoutBotUsernameTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.telegram.mode", "WEBHOOK",
                    "quarkus.telegram.bot-username", "");
        }
    }

    @Test
    public void commandAddressedToBotIgnoredWhenNoBotUsernameConfigured() {
        String json = """
                {
                    "update_id": 9911,
                    "message": {
                        "message_id": 991,
                        "date": 1700000000,
                        "chat": { "id": -100888, "type": "supergroup" },
                        "from": { "id": 2001, "is_bot": false, "first_name": "Mario" },
                        "text": "/start@SomeBot"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(json)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        TelegramMockServer.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMessage"))
                        .withRequestBody(WireMock.containing("-100888")));
    }
}
