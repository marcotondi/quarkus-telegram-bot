package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.test.TelegramBotTestSupport;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramHealthMetricsTest {

    @Inject
    TelegramBotTestSupport botTester;

    @Test
    public void readinessReportsTelegramBot() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("telegram-bot"))
                .body("checks.status", everyItem(equalTo("UP")));
    }

    @Test
    public void metricsExposeUpdateCounters() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200);

        botTester.user(6060L)
                .sendsCommand("/start")
                .assertBotReplied("Ciao");

        String metrics = "";
        for (int attempt = 0; attempt < 20 && !metrics.contains("telegram_updates_received_total"); attempt++) {
            metrics = given()
                    .when().get("/q/metrics")
                    .then()
                    .statusCode(200)
                    .extract().asString();
            if (metrics.contains("telegram_updates_received_total")) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        assertTrue(metrics.contains("telegram_updates_received_total"));
        assertTrue(metrics.contains("telegram_updates_duration_seconds"));
    }
}
