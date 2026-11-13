package io.quarkiverse.telegram.bot.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Telegram bot build-time configuration.
 */
@ConfigMapping(prefix = "quarkus.telegram")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TelegramBotBuildTimeConfig {

    /**
     * Webhook build-time configuration.
     */
    WebhookConfig webhook();

    interface WebhookConfig {
        /**
         * Whether the webhook HTTP endpoint is enabled and mounted on Vert.x.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Path where the webhook endpoint is mounted on the Vert.x HTTP server.
         */
        @WithDefault("/telegram/webhook")
        String path();
    }
}
