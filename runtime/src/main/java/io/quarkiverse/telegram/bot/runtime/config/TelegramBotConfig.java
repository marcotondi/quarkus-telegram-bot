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
     * Optional bot username (without '@').
     * If configured, commands with an explicit target in group chats (e.g. '/start@MyBot')
     * will only match if the target matches this username.
     */
    Optional<String> botUsername();

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
         * Secret token to validate incoming webhook requests via X-Telegram-Bot-Api-Secret-Token.
         */
        Optional<String> secretToken();

        /**
         * Whether a secret token is strictly required. Defaults to true.
         * If false, requests without token or with unconfigured secret token are permitted.
         */
        @WithDefault("true")
        boolean requireSecretToken();

        /**
         * Automatically register webhook on Telegram startup when publicUrl is present.
         */
        @WithDefault("true")
        boolean autoRegister();
    }

    interface SessionConfig {
        /**
         * Automatic expiration TTL for inactive conversation sessions.
         */
        @WithDefault("15m")
        Duration ttl();
    }
}
