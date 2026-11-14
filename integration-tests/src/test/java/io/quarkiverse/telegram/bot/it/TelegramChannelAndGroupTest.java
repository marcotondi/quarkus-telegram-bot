package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.telegram.bot.test.TelegramBotTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramBotTestResource.class)
public class TelegramChannelAndGroupTest {

    @BeforeEach
    void setUp() {
        TelegramBotTestResource.reset();
    }

    private void verifyWithRetry(String urlPattern, String bodySubstring) {
        long deadline = System.currentTimeMillis() + 3000;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                TelegramBotTestResource.getWireMockServer().verify(
                        WireMock.postRequestedFor(WireMock.urlMatching(urlPattern))
                                .withRequestBody(WireMock.containing(bodySubstring)));
                return;
            } catch (AssertionError e) {
                lastError = e;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private void verifyNoRequest(String urlPattern, String bodySubstring) {
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
        }
        TelegramBotTestResource.getWireMockServer().verify(0,
                WireMock.postRequestedFor(WireMock.urlMatching(urlPattern))
                        .withRequestBody(WireMock.containing(bodySubstring)));
    }

    @Test
    public void testChannelPostBroadcast() {
        String json = """
                {
                    "update_id": 901,
                    "channel_post": {
                        "message_id": 10,
                        "date": 1700000000,
                        "chat": { "id": -100123456, "type": "channel", "title": "News Channel" },
                        "text": "Breaking news bulletin"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyWithRetry("/bot.*/sendMessage", "Broadcast in channel: Breaking news bulletin");
    }

    @Test
    public void testEditedChannelPost() {
        String json = """
                {
                    "update_id": 902,
                    "edited_channel_post": {
                        "message_id": 10,
                        "date": 1700000010,
                        "chat": { "id": -100123456, "type": "channel", "title": "News Channel" },
                        "text": "Corrected bulletin"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyWithRetry("/bot.*/sendMessage", "Broadcast in channel: Corrected bulletin");
    }

    @Test
    public void testGroupRulesInSupergroup() {
        String json = """
                {
                    "update_id": 903,
                    "message": {
                        "message_id": 11,
                        "date": 1700000000,
                        "chat": { "id": -200999, "type": "supergroup", "title": "Community" },
                        "from": { "id": 777, "is_bot": false, "first_name": "Bob" },
                        "text": "/rules"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyWithRetry("/bot.*/sendMessage", "Regole del gruppo: Rispetta i membri.");
    }

    @Test
    public void testGroupRulesInGroup() {
        String json = """
                {
                    "update_id": 904,
                    "message": {
                        "message_id": 12,
                        "date": 1700000000,
                        "chat": { "id": -300888, "type": "group", "title": "Small Group" },
                        "from": { "id": 888, "is_bot": false, "first_name": "Charlie" },
                        "text": "/rules"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyWithRetry("/bot.*/sendMessage", "Regole del gruppo: Rispetta i membri.");
    }

    @Test
    public void testGroupRulesIgnoredInPrivateChat() {
        String json = """
                {
                    "update_id": 905,
                    "message": {
                        "message_id": 13,
                        "date": 1700000000,
                        "chat": { "id": 5555, "type": "private", "first_name": "Dave" },
                        "from": { "id": 5555, "is_bot": false, "first_name": "Dave" },
                        "text": "/rules"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyNoRequest("/bot.*/sendMessage", "Regole del gruppo");
    }

    @Test
    public void testSecretAllowedInPrivateChat() {
        String json = """
                {
                    "update_id": 906,
                    "message": {
                        "message_id": 14,
                        "date": 1700000000,
                        "chat": { "id": 6666, "type": "private", "first_name": "Eve" },
                        "from": { "id": 6666, "is_bot": false, "first_name": "Eve" },
                        "text": "/secret"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyWithRetry("/bot.*/sendMessage", "Segreto privato: 42");
    }

    @Test
    public void testSecretIgnoredInGroupChat() {
        String json = """
                {
                    "update_id": 907,
                    "message": {
                        "message_id": 15,
                        "date": 1700000000,
                        "chat": { "id": -400777, "type": "supergroup", "title": "Public Hub" },
                        "from": { "id": 6666, "is_bot": false, "first_name": "Eve" },
                        "text": "/secret"
                    }
                }
                """;

        given().contentType(ContentType.JSON).body(json)
                .when().post("/telegram/webhook")
                .then().statusCode(200);

        verifyNoRequest("/bot.*/sendMessage", "Segreto privato: 42");
    }
}
