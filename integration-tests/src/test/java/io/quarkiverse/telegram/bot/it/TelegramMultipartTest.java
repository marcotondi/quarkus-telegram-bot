package io.quarkiverse.telegram.bot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.InputFile;
import io.quarkiverse.telegram.bot.runtime.model.InputMediaPhoto;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramMultipartTest {

    @Inject
    TelegramBotClient client;

    @BeforeEach
    public void setupStubs() {
        // Stub sendPhoto
        TelegramMockServer.getWireMockServer().stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendPhoto"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 801,
                                    "date": 1700000000,
                                    "chat": { "id": 1001, "type": "private" },
                                    "caption": "Photo caption"
                                  }
                                }
                                """)));

        // Stub sendDocument
        TelegramMockServer.getWireMockServer().stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendDocument"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 802,
                                    "date": 1700000000,
                                    "chat": { "id": 1001, "type": "private" },
                                    "caption": "Doc caption"
                                  }
                                }
                                """)));

        // Stub sendAudio
        TelegramMockServer.getWireMockServer().stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendAudio"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": {
                                    "message_id": 803,
                                    "date": 1700000000,
                                    "chat": { "id": 1001, "type": "private" },
                                    "caption": "Audio caption"
                                  }
                                }
                                """)));

        // Stub sendMediaGroup
        TelegramMockServer.getWireMockServer().stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendMediaGroup"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "ok": true,
                                  "result": [
                                    {
                                      "message_id": 804,
                                      "date": 1700000000,
                                      "chat": { "id": 1001, "type": "private" }
                                    },
                                    {
                                      "message_id": 805,
                                      "date": 1700000000,
                                      "chat": { "id": 1001, "type": "private" }
                                    }
                                  ]
                                }
                                """)));
    }

    @Test
    public void testSendPhotoWithFileId() {
        InputFile photo = InputFile.fromFileId("agb123photo");
        Message msg = client.sendPhoto(1001, photo, "Photo caption", InlineKeyboard.of("Click", "cb_click"))
                .await().indefinitely();

        assertNotNull(msg);
        assertEquals(801L, msg.messageId());

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendPhoto"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.containing("\"photo\":\"agb123photo\"")));
    }

    @Test
    public void testSendPhotoWithBinaryBytes() {
        byte[] bytes = "fake image bytes".getBytes(StandardCharsets.UTF_8);
        InputFile photo = InputFile.fromBytes(bytes, "avatar.png", "image/png");

        Message msg = client.sendPhoto(1001, photo, "Photo caption", InlineKeyboard.of("Click", "cb_click"))
                .await().indefinitely();

        assertNotNull(msg);
        assertEquals(801L, msg.messageId());

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendPhoto"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }

    @Test
    public void testSendPhotoWithBinaryPath() throws Exception {
        File tempFile = File.createTempFile("test-photo", ".jpg");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), "test jpg content");

        InputFile photo = InputFile.fromPath(tempFile.toPath());
        Message msg = client.sendPhoto(1001, photo, "Photo caption").await().indefinitely();

        assertNotNull(msg);
        assertEquals(801L, msg.messageId());

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendPhoto"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }

    @Test
    public void testSendDocumentWithBinaryBytes() {
        byte[] bytes = "dummy pdf content".getBytes(StandardCharsets.UTF_8);
        InputFile doc = InputFile.fromBytes(bytes, "manual.pdf", "application/pdf");

        Message msg = client.sendDocument(1001, doc, "Doc caption").await().indefinitely();

        assertNotNull(msg);
        assertEquals(802L, msg.messageId());

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendDocument"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }

    @Test
    public void testSendAudioWithFileId() {
        InputFile audio = InputFile.fromFileId("audio_id_456");
        Message msg = client.sendAudio(1001, audio, "Audio caption").await().indefinitely();

        assertNotNull(msg);
        assertEquals(803L, msg.messageId());

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendAudio"))
                .withHeader("Content-Type", WireMock.containing("application/json"))
                .withRequestBody(WireMock.containing("\"audio\":\"audio_id_456\"")));
    }

    @Test
    public void testSendMediaGroupWithAttachments() {
        InputMediaPhoto p1 = InputMediaPhoto.builder()
                .type("photo")
                .media("attach://photo1")
                .caption("First photo")
                .build();
        InputMediaPhoto p2 = InputMediaPhoto.builder()
                .type("photo")
                .media("https://example.com/photo2.jpg")
                .caption("Second photo")
                .build();

        byte[] p1Bytes = "photo1 bytes".getBytes(StandardCharsets.UTF_8);
        Map<String, InputFile> attachments = Map.of("photo1", InputFile.fromBytes(p1Bytes, "p1.jpg", "image/jpeg"));

        List<Message> messages = client.sendMediaGroup(1001, List.of(p1, p2), attachments).await().indefinitely();

        assertNotNull(messages);
        assertEquals(2, messages.size());
        assertEquals(804L, messages.get(0).messageId());
        assertEquals(805L, messages.get(1).messageId());

        TelegramMockServer.getWireMockServer().verify(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendMediaGroup"))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data")));
    }
}
