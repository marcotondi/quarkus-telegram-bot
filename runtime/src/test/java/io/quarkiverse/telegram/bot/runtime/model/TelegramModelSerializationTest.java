package io.quarkiverse.telegram.bot.runtime.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TelegramModelSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void testSendChatActionSerialization() throws Exception {
        SendChatAction action = new SendChatAction(12345L, ChatAction.TYPING);
        String json = objectMapper.writeValueAsString(action);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(12345, node.get("chat_id").asLong());
        assertEquals("typing", node.get("action").asText());
        assertFalse(node.has("message_thread_id"));
        assertFalse(node.has("business_connection_id"));
    }

    @Test
    void testChatActionEnumValues() {
        assertEquals("typing", ChatAction.TYPING.value());
        assertEquals("upload_photo", ChatAction.UPLOAD_PHOTO.value());
        assertEquals("record_video", ChatAction.RECORD_VIDEO.value());
        assertEquals("upload_video", ChatAction.UPLOAD_VIDEO.value());
        assertEquals("record_voice", ChatAction.RECORD_VOICE.value());
        assertEquals("upload_voice", ChatAction.UPLOAD_VOICE.value());
        assertEquals("upload_document", ChatAction.UPLOAD_DOCUMENT.value());
        assertEquals("choose_sticker", ChatAction.CHOOSE_STICKER.value());
        assertEquals("find_location", ChatAction.FIND_LOCATION.value());
        assertEquals("record_video_note", ChatAction.RECORD_VIDEO_NOTE.value());
        assertEquals("upload_video_note", ChatAction.UPLOAD_VIDEO_NOTE.value());
    }

    @Test
    void testDeleteMessageSerialization() throws Exception {
        DeleteMessage deleteMessage = new DeleteMessage(1001L, 42L);
        String json = objectMapper.writeValueAsString(deleteMessage);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(1001, node.get("chat_id").asLong());
        assertEquals(42, node.get("message_id").asLong());
    }

    @Test
    void testInputFilePatternMatching() {
        InputFile fileId = InputFile.fromFileId("ag_12345");
        assertTrue(fileId instanceof InputFile.FileId);
        assertEquals("ag_12345", ((InputFile.FileId) fileId).value());

        InputFile url = InputFile.fromUrl("https://example.com/logo.png");
        assertTrue(url instanceof InputFile.Url);
        assertEquals("https://example.com/logo.png", ((InputFile.Url) url).value());

        InputFile binaryPath = InputFile.fromPath(Path.of("/tmp/pic.jpg"));
        assertTrue(binaryPath instanceof InputFile.BinaryPath);
        assertEquals("pic.jpg", ((InputFile.BinaryPath) binaryPath).fileName());
        assertEquals("application/octet-stream", ((InputFile.BinaryPath) binaryPath).contentType());

        byte[] bytes = new byte[] { 1, 2, 3 };
        InputFile binaryBytes = InputFile.fromBytes(bytes, "doc.pdf", "application/pdf");
        assertTrue(binaryBytes instanceof InputFile.BinaryBytes);
        assertEquals("doc.pdf", ((InputFile.BinaryBytes) binaryBytes).fileName());
    }

    @Test
    void testInlineKeyboardMarkupSerialization() throws Exception {
        InlineKeyboardButton b1 = InlineKeyboardButton.builder()
                .text("Yes")
                .callbackData("btn_yes")
                .build();
        InlineKeyboardButton b2 = InlineKeyboardButton.builder()
                .text("No")
                .callbackData("btn_no")
                .build();

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(b1, b2)));
        String json = objectMapper.writeValueAsString(markup);
        JsonNode node = objectMapper.readTree(json);

        assertTrue(node.has("inline_keyboard"));
        JsonNode keyboard = node.get("inline_keyboard");
        assertEquals(1, keyboard.size());
        assertEquals(2, keyboard.get(0).size());
        assertEquals("Yes", keyboard.get(0).get(0).get("text").asText());
        assertEquals("btn_yes", keyboard.get(0).get(0).get("callback_data").asText());
    }

    @Test
    void testEditMessageReplyMarkupSerialization() throws Exception {
        InlineKeyboardButton btn = InlineKeyboardButton.builder()
                .text("Refresh")
                .callbackData("refresh")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(btn)));

        EditMessageReplyMarkup req = new EditMessageReplyMarkup(1001L, 55L, markup);
        String json = objectMapper.writeValueAsString(req);
        JsonNode node = objectMapper.readTree(json);

        assertEquals(1001, node.get("chat_id").asLong());
        assertEquals(55, node.get("message_id").asLong());
        assertTrue(node.has("reply_markup"));
    }

    @Test
    void testSendMediaJsonRequests() throws Exception {
        SendVideoJsonRequest video = new SendVideoJsonRequest(1001L, "vid_123", "Nice video", null);
        String videoJson = objectMapper.writeValueAsString(video);
        JsonNode videoNode = objectMapper.readTree(videoJson);
        assertEquals(1001, videoNode.get("chat_id").asLong());
        assertEquals("vid_123", videoNode.get("video").asText());
        assertEquals("Nice video", videoNode.get("caption").asText());

        SendAnimationJsonRequest anim = new SendAnimationJsonRequest("chat_str", "anim_123", "GIF", null);
        String animJson = objectMapper.writeValueAsString(anim);
        JsonNode animNode = objectMapper.readTree(animJson);
        assertEquals("chat_str", animNode.get("chat_id").asText());
        assertEquals("anim_123", animNode.get("animation").asText());

        SendVoiceJsonRequest voice = new SendVoiceJsonRequest(1001L, "voice_123", null, null);
        String voiceJson = objectMapper.writeValueAsString(voice);
        JsonNode voiceNode = objectMapper.readTree(voiceJson);
        assertEquals("voice_123", voiceNode.get("voice").asText());
    }
}
