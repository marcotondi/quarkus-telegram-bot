package io.quarkiverse.telegram.bot.runtime.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

import io.quarkiverse.telegram.bot.runtime.TelegramBotStatus;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.transport.PollingRunner;

/**
 * Readiness check registered only when {@code quarkus-smallrye-health} is on the classpath.
 * Reports the state of the transport that the extension actually started.
 */
@Readiness
@ApplicationScoped
public class TelegramBotHealthCheck implements HealthCheck {

    static final String NAME = "telegram-bot";

    @Inject
    TelegramBotStatus status;

    @Inject
    PollingRunner pollingRunner;

    @Inject
    TelegramBotConfig config;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME);

        if (config.token().isEmpty() || config.token().get().isBlank()) {
            return response.down().withData("reason", "quarkus.telegram.token is not configured").build();
        }

        return switch (status.getTransport()) {
            case NONE -> response.down().withData("reason", "no transport active").build();
            case POLLING -> pollingResponse(response);
            case WEBHOOK -> webhookResponse(response);
        };
    }

    private HealthCheckResponse pollingResponse(HealthCheckResponseBuilder response) {
        if (!pollingRunner.isRunning()) {
            return response.down().withData("transport", "polling").withData("reason", "polling loop not running").build();
        }
        if (status.hasError()) {
            return response.down().withData("transport", "polling").withData("error", status.getLastError()).build();
        }
        return response.up().withData("transport", "polling").build();
    }

    private HealthCheckResponse webhookResponse(HealthCheckResponseBuilder response) {
        if (status.hasError()) {
            return response.down().withData("transport", "webhook").withData("error", status.getLastError()).build();
        }
        return response.up().withData("transport", "webhook").build();
    }
}
