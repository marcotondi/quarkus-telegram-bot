package io.quarkiverse.telegram.bot.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.ChatAction;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboardButton;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboardMarkup;
import io.quarkiverse.telegram.bot.runtime.model.InputFile;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.test.TelegramBotTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(TelegramBotTestResource.class)
public class TelegramClientMediaIntegrationTest {

    @Inject
    TelegramBotClient client;

    @Test
    public void testSendVideoWithLongAndStringChatId() {
        Message res1 = client.sendVideo(1001L, InputFile.fromFileId("video_file_1"), "Caption 1").await().indefinitely();
        assertNotNull(res1);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVideo"))
                        .withRequestBody(WireMock.containing("video_file_1"))
                        .withRequestBody(WireMock.containing("1001")));

        Message res2 = client.sendVideo("@channel_tag", InputFile.fromFileId("video_file_2"), "Caption 2").await()
                .indefinitely();
        assertNotNull(res2);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVideo"))
                        .withRequestBody(WireMock.containing("video_file_2"))
                        .withRequestBody(WireMock.containing("@channel_tag")));
    }

    @Test
    public void testSendAnimationWithLongAndStringChatId() {
        Message res1 = client.sendAnimation(2002L, InputFile.fromFileId("anim_1"), "GIF 1").await().indefinitely();
        assertNotNull(res1);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendAnimation"))
                        .withRequestBody(WireMock.containing("anim_1"))
                        .withRequestBody(WireMock.containing("2002")));

        Message res2 = client.sendAnimation("-1009999", InputFile.fromFileId("anim_2"), "GIF 2").await().indefinitely();
        assertNotNull(res2);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendAnimation"))
                        .withRequestBody(WireMock.containing("anim_2"))
                        .withRequestBody(WireMock.containing("-1009999")));
    }

    @Test
    public void testSendVoiceWithLongAndStringChatId() {
        Message res1 = client.sendVoice(3003L, InputFile.fromFileId("voice_1")).await().indefinitely();
        assertNotNull(res1);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVoice"))
                        .withRequestBody(WireMock.containing("voice_1"))
                        .withRequestBody(WireMock.containing("3003")));

        Message res2 = client.sendVoice("-1008888", InputFile.fromFileId("voice_2"), "Voice note").await().indefinitely();
        assertNotNull(res2);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendVoice"))
                        .withRequestBody(WireMock.containing("voice_2"))
                        .withRequestBody(WireMock.containing("-1008888")));
    }

    @Test
    public void testSendChatActionWithChatActionEnum() {
        Boolean b1 = client.sendChatAction(4004L, ChatAction.TYPING).await().indefinitely();
        assertTrue(b1);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendChatAction"))
                        .withRequestBody(WireMock.containing("4004"))
                        .withRequestBody(WireMock.containing("typing")));

        Boolean b2 = client.sendChatAction("@community", ChatAction.UPLOAD_PHOTO).await().indefinitely();
        assertTrue(b2);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/sendChatAction"))
                        .withRequestBody(WireMock.containing("@community"))
                        .withRequestBody(WireMock.containing("upload_photo")));
    }

    @Test
    public void testDeleteMessageProgrammatic() {
        Boolean deleted1 = client.deleteMessage(5005L, 77L).await().indefinitely();
        assertTrue(deleted1);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/deleteMessage"))
                        .withRequestBody(WireMock.containing("5005"))
                        .withRequestBody(WireMock.containing("77")));

        Boolean deleted2 = client.deleteMessage("-1005005", 88L).await().indefinitely();
        assertTrue(deleted2);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/deleteMessage"))
                        .withRequestBody(WireMock.containing("-1005005"))
                        .withRequestBody(WireMock.containing("88")));
    }

    @Test
    public void testEditMessageReplyMarkupProgrammatic() {
        InlineKeyboardButton btn = InlineKeyboardButton.builder()
                .text("Updated Button")
                .callbackData("btn_updated")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(btn)));

        Message edited = client.editMessageReplyMarkup(6006L, 99L, markup).await().indefinitely();
        assertNotNull(edited);

        TelegramBotTestResource.getWireMockServer().verify(
                WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/editMessageReplyMarkup"))
                        .withRequestBody(WireMock.containing("6006"))
                        .withRequestBody(WireMock.containing("99"))
                        .withRequestBody(WireMock.containing("btn_updated")));
    }
}
