package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(TelegramWebhookDisabledHealthTest.Profile.class)
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramWebhookDisabledHealthTest {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.telegram.mode", "WEBHOOK",
                    "quarkus.telegram.webhook.enabled", "false");
        }
    }

    @Test
    public void readinessReportsDownWhenWebhookDisabled() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(503)
                .body("status", equalTo("DOWN"))
                .body("checks.name", hasItem("telegram-bot"))
                .body("checks.find { it.name == 'telegram-bot' }.status", equalTo("DOWN"))
                .body("checks.find { it.name == 'telegram-bot' }.data.reason", equalTo("no transport active"));
    }
}
