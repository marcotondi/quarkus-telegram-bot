package io.quarkiverse.telegram.bot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramClientRateLimitRetryTest {

    @Inject
    TelegramBotClient client;

    @Test
    public void clientRetriesOn429WithRetryAfter() {
        TelegramMockServer.getWireMockServer().stubFor(WireMock.post(WireMock.urlMatching("/bot.*/getMe"))
                .inScenario("429 Retry Scenario")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "ok": false,
                                    "error_code": 429,
                                    "description": "Too Many Requests: retry after 1",
                                    "parameters": {
                                        "retry_after": 1
                                    }
                                }
                                """))
                .willSetStateTo("Retried"));

        TelegramMockServer.getWireMockServer().stubFor(WireMock.post(WireMock.urlMatching("/bot.*/getMe"))
                .inScenario("429 Retry Scenario")
                .whenScenarioStateIs("Retried")
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "ok": true,
                                    "result": {
                                        "id": 123456,
                                        "is_bot": true,
                                        "first_name": "TestBot"
                                    }
                                }
                                """)));

        User me = client.getMe().await().indefinitely();
        assertNotNull(me);
        assertEquals(123456, me.id());
    }
}
