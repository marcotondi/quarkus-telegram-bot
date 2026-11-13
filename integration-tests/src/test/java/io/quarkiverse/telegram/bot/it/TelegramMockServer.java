package io.quarkiverse.telegram.bot.it;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class TelegramMockServer implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Stub deleteWebhook
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/deleteWebhook"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // Stub getMe
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/getMe"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"id\":123456,\"is_bot\":true,\"first_name\":\"TestBot\",\"username\":\"test_bot\"}}")));

        // Stub sendMessage
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/sendMessage"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"},\"text\":\"Echo\"}}")));

        // Stub editMessageText
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/editMessageText"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                "{\"ok\":true,\"result\":{\"message_id\":999,\"date\":1700000000,\"chat\":{\"id\":1001,\"type\":\"private\"},\"text\":\"Edited text\"}}")));

        // Stub answerCallbackQuery
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/answerCallbackQuery"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

        // Stub setMyCommands
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching("/bot.*/setMyCommands"))
                .willReturn(WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ok\":true,\"result\":true}")));

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
        }
    }
}
