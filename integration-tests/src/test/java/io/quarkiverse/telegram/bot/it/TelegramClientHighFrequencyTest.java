package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.ChatAction;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.InputFile;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramClientHighFrequencyTest {

    @Inject
    TelegramBotClient client;

    @Test
    public void testDeleteMessage() {
        Boolean result = client.deleteMessage(1001L, 555L).await().indefinitely();
        assertTrue(result);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/deleteMessage"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                .withRequestBody(WireMock.matchingJsonPath("$.message_id", WireMock.equalTo("555"))));
    }

    @Test
    public void testEditMessageReplyMarkup() {
        Message msg = client.editMessageReplyMarkup(1001L, 777L, InlineKeyboard.of("Nuovo Bottone", "cb_new"))
                .await().indefinitely();
        assertNotNull(msg);

        TelegramMockServer.getWireMockServer()
                .verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/editMessageReplyMarkup"))
                        .withHeader("Content-Type", WireMock.containing("application/json"))
                        .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                        .withRequestBody(WireMock.matchingJsonPath("$.message_id", WireMock.equalTo("777")))
                        .withRequestBody(WireMock.matchingJsonPath("$.reply_markup.inline_keyboard[0][0].text",
                                WireMock.equalTo("Nuovo Bottone"))));

        // Test clearing reply markup
        Message clearedMsg = client.editMessageReplyMarkup(1001L, 777L).await().indefinitely();
        assertNotNull(clearedMsg);
    }

    @Test
    public void testEditInlineMessageReplyMarkup() {
        Boolean result = client.editInlineMessageReplyMarkup("inline_msg_999", InlineKeyboard.of("Inline Btn", "btn_inline"))
                .await().indefinitely();
        assertNotNull(result);

        TelegramMockServer.getWireMockServer()
                .verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/editMessageReplyMarkup"))
                        .withHeader("Content-Type", WireMock.containing("application/json"))
                        .withRequestBody(WireMock.matchingJsonPath("$.inline_message_id", WireMock.equalTo("inline_msg_999"))));
    }

    @Test
    public void testSendChatAction() {
        Boolean res1 = client.sendChatAction(1001L, ChatAction.TYPING).await().indefinitely();
        assertTrue(res1);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendChatAction"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                .withRequestBody(WireMock.matchingJsonPath("$.action", WireMock.equalTo("typing"))));

        Boolean res2 = client.sendChatAction(1001L, "upload_document").await().indefinitely();
        assertTrue(res2);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendChatAction"))
                .withRequestBody(WireMock.matchingJsonPath("$.action", WireMock.equalTo("upload_document"))));
    }

    @Test
    public void testSendVideoJsonAndMultipart() {
        // JSON
        InputFile urlVideo = InputFile.fromUrl("https://example.com/video.mp4");
        Message msg1 = client.sendVideo(1001L, urlVideo, "Sample Video", InlineKeyboard.of("Watch", "video_watch"))
                .await().indefinitely();
        assertNotNull(msg1);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVideo"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.matchingJsonPath("$.video", WireMock.equalTo("https://example.com/video.mp4")))
                .withRequestBody(WireMock.matchingJsonPath("$.caption", WireMock.equalTo("Sample Video"))));

        // Multipart
        byte[] bytes = "fake mp4 content".getBytes(StandardCharsets.UTF_8);
        InputFile binaryVideo = InputFile.fromBytes(bytes, "movie.mp4", "video/mp4");
        Message msg2 = client.sendVideo(1001L, binaryVideo, "Uploaded Clip").await().indefinitely();
        assertNotNull(msg2);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVideo"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }

    @Test
    public void testSendAnimationJsonAndMultipart() {
        // JSON
        InputFile urlAnim = InputFile.fromUrl("https://example.com/cat.gif");
        Message msg1 = client.sendAnimation(1001L, urlAnim, "Funny cat", InlineKeyboard.of("Meow", "cat_meow"))
                .await().indefinitely();
        assertNotNull(msg1);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendAnimation"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.matchingJsonPath("$.animation", WireMock.equalTo("https://example.com/cat.gif")))
                .withRequestBody(WireMock.matchingJsonPath("$.caption", WireMock.equalTo("Funny cat"))));

        // Multipart
        byte[] bytes = "fake gif content".getBytes(StandardCharsets.UTF_8);
        InputFile binaryAnim = InputFile.fromBytes(bytes, "anim.gif", "image/gif");
        Message msg2 = client.sendAnimation(1001L, binaryAnim, "Uploaded GIF").await().indefinitely();
        assertNotNull(msg2);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendAnimation"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }

    @Test
    public void testSendVoiceJsonAndMultipart() {
        // JSON
        InputFile fileIdVoice = InputFile.fromFileId("voice_file_abc123");
        Message msg1 = client.sendVoice(1001L, fileIdVoice, "Voice note").await().indefinitely();
        assertNotNull(msg1);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVoice"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.matchingJsonPath("$.voice", WireMock.equalTo("voice_file_abc123")))
                .withRequestBody(WireMock.matchingJsonPath("$.caption", WireMock.equalTo("Voice note"))));

        // Multipart
        byte[] bytes = "fake ogg voice content".getBytes(StandardCharsets.UTF_8);
        InputFile binaryVoice = InputFile.fromBytes(bytes, "voice.ogg", "audio/ogg");
        Message msg2 = client.sendVoice(1001L, binaryVoice).await().indefinitely();
        assertNotNull(msg2);

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVoice"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }

    @Test
    public void testWebhookDispatchesDeleteMessage() throws Exception {
        String updateJson = """
                {
                    "update_id": 950,
                    "message": {
                        "message_id": 100,
                        "date": 1700000000,
                        "chat": {
                            "id": 1001,
                            "type": "private"
                        },
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Luigi"
                        },
                        "text": "/test-delete"
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

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/deleteMessage"))
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                .withRequestBody(WireMock.matchingJsonPath("$.message_id", WireMock.equalTo("12345"))));
    }

    @Test
    public void testWebhookDispatchesSendChatAction() throws Exception {
        String updateJson = """
                {
                    "update_id": 951,
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
                            "first_name": "Luigi"
                        },
                        "text": "/test-action"
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

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendChatAction"))
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                .withRequestBody(WireMock.matchingJsonPath("$.action", WireMock.equalTo("typing"))));
    }

    @Test
    public void testWebhookDispatchesEditMessageReplyMarkup() throws Exception {
        String updateJson = """
                {
                    "update_id": 952,
                    "callback_query": {
                        "id": "cb_query_952",
                        "from": {
                            "id": 2001,
                            "is_bot": false,
                            "first_name": "Luigi"
                        },
                        "message": {
                            "message_id": 999,
                            "date": 1700000000,
                            "chat": {
                                "id": 1001,
                                "type": "private"
                            },
                            "text": "Initial text"
                        },
                        "data": "markup_test"
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

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/editMessageReplyMarkup"))
                .withRequestBody(WireMock.matchingJsonPath("$.chat_id", WireMock.equalTo("1001")))
                .withRequestBody(WireMock.matchingJsonPath("$.message_id", WireMock.equalTo("999")))
                .withRequestBody(
                        WireMock.matchingJsonPath("$.reply_markup.inline_keyboard[0][0].text", WireMock.equalTo("Updated"))));

        // Verify callback query spinner was dismissed
        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerCallbackQuery"))
                .withRequestBody(WireMock.matchingJsonPath("$.callback_query_id", WireMock.equalTo("cb_query_952"))));
    }

    private void verifyWithRetry(RequestPatternBuilder pattern) throws InterruptedException {
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
