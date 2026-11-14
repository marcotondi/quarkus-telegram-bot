package io.quarkiverse.telegram.bot.test;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Pre-configured WireMock test resource for Telegram Bot API.
 * Automatically stubs Telegram endpoints (getMe, sendMessage, editMessageText, answerCallbackQuery, etc.)
 * on dynamic ports and binds the required Quarkus configuration properties.
 */
public class TelegramBotTestResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    public static void setWireMockServer(WireMockServer server) {
        wireMockServer = server;
    }

    /**
     * Resets recorded requests on the WireMock server without removing stubs.
     */
    public static void reset() {
        if (wireMockServer != null) {
            wireMockServer.resetRequests();
        }
    }

    /**
     * Resets both recorded requests and stubs, then restores default stubs.
     */
    public static void resetAll() {
        if (wireMockServer != null) {
            wireMockServer.resetAll();
            configureDefaultStubs(wireMockServer);
        }
    }

    @Override
    public Map<String, String> start() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
            wireMockServer.start();
        }
        WireMock.configureFor("localhost", wireMockServer.port());
        configureDefaultStubs(wireMockServer);

        return Map.of(
                "quarkus.telegram.client.base-url", wireMockServer.baseUrl(),
                "quarkus.telegram.token", "123456:TEST_TOKEN",
                "quarkus.telegram.bot-username", "test_bot",
                "quarkus.telegram.mode", "WEBHOOK");
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    /**
     * Configures default stubs for Telegram Bot API methods.
     */
    public static void configureDefaultStubs(WireMockServer server) {
        // deleteWebhook
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/deleteWebhook"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // setWebhook
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/setWebhook"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // getMe
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/getMe"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"id\":123456,\"is_bot\":true,\"first_name\":\"TestBot\",\"username\":\"test_bot\"}}")));

        // sendMessage
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendMessage"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"},\"text\":\"Echo\"}}")));

        // editMessageText
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/editMessageText"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"},\"text\":\"Edited text\"}}")));

        // editMessageReplyMarkup
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/editMessageReplyMarkup"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        // answerCallbackQuery
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/answerCallbackQuery"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // answerInlineQuery
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/answerInlineQuery"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // setMyCommands
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/setMyCommands"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // deleteMessage
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/deleteMessage"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // sendChatAction
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendChatAction"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // Media endpoints
        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendPhoto"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendDocument"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendAudio"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendVoice"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendVideo"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendAnimation"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}}")));

        server.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendMediaGroup"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":[{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"}}]}")));
    }
}
