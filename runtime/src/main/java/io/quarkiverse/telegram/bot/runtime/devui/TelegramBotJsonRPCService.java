package io.quarkiverse.telegram.bot.runtime.devui;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;

@ApplicationScoped
public class TelegramBotJsonRPCService {

    @Inject
    TelegramBotConfig config;

    public String getBotUsername() {
        return config.botUsername().map(u -> "@" + u).orElse("(not configured)");
    }

    public String getTransportMode() {
        return config.mode().name();
    }

    public String getTokenStatus() {
        return config.token().filter(t -> !t.isBlank()).isPresent() ? "Configured" : "Missing";
    }

    public String getSessionStore() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.telegram.session.store", String.class)
                .orElse("memory");
    }

    public String getWebhookPath() {
        boolean enabled = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.telegram.webhook.enabled", Boolean.class)
                .orElse(true);
        if (!enabled) {
            return "Disabled";
        }
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.telegram.webhook.path", String.class)
                .orElse("/telegram/webhook");
    }

    public BotInfo getBotInfo() {
        return new BotInfo(
                getBotUsername(),
                getTransportMode(),
                getTokenStatus(),
                getSessionStore(),
                getWebhookPath());
    }

    public record BotInfo(
            String username,
            String mode,
            String tokenStatus,
            String sessionStore,
            String webhookPath) {
    }
}
