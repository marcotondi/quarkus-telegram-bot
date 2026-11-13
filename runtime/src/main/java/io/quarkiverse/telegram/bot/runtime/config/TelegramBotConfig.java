package io.quarkiverse.telegram.bot.runtime.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Telegram bot runtime configuration.
 */
@ConfigMapping(prefix = "quarkus.telegram")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TelegramBotConfig {

    /**
     * Token issued by @BotFather.
     */
    Optional<String> token();

    /**
     * Transport mode: AUTO selects POLLING in dev/test, WEBHOOK in prod.
     */
    @WithDefault("AUTO")
    TransportMode mode();

    /**
     * Telegram HTTP client configuration.
     */
    ClientConfig client();

    /**
     * Telegram Long Polling configuration.
     */
    PollingConfig polling();

    /**
     * Telegram Webhook configuration.
     */
    WebhookConfig webhook();

    /**
     * Session and conversation state configuration.
     */
    SessionConfig session();

    enum TransportMode {
        AUTO,
        POLLING,
        WEBHOOK
    }

    interface ClientConfig {
        /**
         * Base URL of the Telegram Bot API server.
         */
        @WithDefault("https://api.telegram.org")
        String baseUrl();

        /**
         * Extended timeout for multipart file uploads up to 50 MB.
         */
        @WithDefault("60s")
        Duration uploadTimeout();
    }

    interface PollingConfig {
        /**
         * Long polling timeout sent to Telegram in getUpdates.
         */
        @WithDefault("30s")
        Duration timeout();

        /**
         * Maximum number of updates to receive per poll.
         */
        @WithDefault("100")
        int limit();

        /**
         * Whether polling is enabled.
         */
        @WithDefault("true")
        boolean enabled();
    }

    interface WebhookConfig {
        /**
         * Public HTTPS URL of the application (e.g. https://bot.example.com).
         */
        Optional<String> publicUrl();

        /**
         * Path where the webhook endpoint is mounted.
         */
        @WithDefault("/telegram/webhook")
        String path();

        /**
         * Secret token to validate incoming webhook requests via X-Telegram-Bot-Api-Secret-Token.
         */
        Optional<String> secretToken();

        /**
         * Automatically register webhook on Telegram startup when publicUrl is present.
         */
        @WithDefault("true")
        boolean autoRegister();
    }

    interface SessionConfig {
        /**
         * State storage backend: memory or redis.
         */
        @WithDefault("memory")
        String store();

        /**
         * Automatic expiration TTL for inactive conversation sessions.
         */
        @WithDefault("15m")
        Duration ttl();
    }
}
