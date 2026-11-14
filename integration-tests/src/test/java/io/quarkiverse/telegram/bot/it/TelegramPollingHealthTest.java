package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TelegramPollingHealthTest.Profile.class)
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramPollingHealthTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.telegram.mode", "POLLING",
                    "quarkus.telegram.polling.enabled", "true",
                    "quarkus.telegram.polling.timeout", "0s",
                    "quarkus.telegram.webhook.enabled", "false");
        }
    }

    @Test
    public void pollingHealthReportsDownOnNetworkError() {
        TelegramMockServer.getWireMockServer().stubFor(
                WireMock.post(WireMock.urlMatching("/bot.*/getUpdates"))
                        .willReturn(WireMock.aResponse().withStatus(500).withBody("Internal Server Error")));

        for (int i = 0; i < 50; i++) {
            int statusCode = given().when().get("/q/health/ready").then().extract().statusCode();
            if (statusCode == 503) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(503)
                .body("status", equalTo("DOWN"))
                .body("checks.name", hasItem("telegram-bot"))
                .body("checks.find { it.name == 'telegram-bot' }.status", equalTo("DOWN"))
                .body("checks.find { it.name == 'telegram-bot' }.data.transport", equalTo("polling"))
                .body("checks.find { it.name == 'telegram-bot' }.data.error", containsString("polling:"));
    }
}
