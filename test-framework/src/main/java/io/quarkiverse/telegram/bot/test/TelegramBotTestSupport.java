package io.quarkiverse.telegram.bot.test;

import java.time.Duration;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * Developer testing support for Quarkus Telegram Bot.
 * Provides pre-configured Telegram mocks and fluent conversation simulation:
 *
 * <pre>{@code
 * botTester.user(12345L)
 *         .sendsCommand("/start")
 *         .assertBotReplied("Benvenuto!")
 *         .clicksButton("p2_film")
 *         .assertMessageEdited();
 * }</pre>
 */
@ApplicationScoped
public class TelegramBotTestSupport implements QuarkusTestResourceLifecycleManager {

    private final TelegramBotTestResource resourceDelegate = new TelegramBotTestResource();
    private String webhookPath;
    private String secretToken;
    private Duration defaultTimeout = Duration.ofSeconds(3);
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public TelegramBotTestSupport() {
    }

    public static TelegramBotTestSupport create() {
        return new TelegramBotTestSupport();
    }

    /**
     * Static shortcut to start a simulated conversation.
     *
     * @param userId the Telegram user ID
     * @return a new ConversationSimulator
     */
    public static ConversationSimulator simulate(long userId) {
        return new TelegramBotTestSupport().user(userId);
    }

    /**
     * Static shortcut to start a simulated conversation with explicit chat ID.
     *
     * @param userId the Telegram user ID
     * @param chatId the Telegram chat ID
     * @return a new ConversationSimulator
     */
    public static ConversationSimulator simulate(long userId, long chatId) {
        return new TelegramBotTestSupport().user(userId, chatId);
    }

    @Override
    public Map<String, String> start() {
        return resourceDelegate.start();
    }

    @Override
    public void stop() {
        resourceDelegate.stop();
    }

    /**
     * Returns the underlying WireMockServer instance.
     */
    public WireMockServer getWireMockServer() {
        return TelegramBotTestResource.getWireMockServer();
    }

    /**
     * Resets recorded WireMock requests.
     */
    public void reset() {
        TelegramBotTestResource.reset();
    }

    /**
     * Resets recorded WireMock requests and restores default stubs.
     */
    public void resetAll() {
        TelegramBotTestResource.resetAll();
    }

    /**
     * Starts a fluent conversation simulation for the given user ID.
     * Default chat ID is equal to user ID (private chat).
     *
     * @param userId Telegram user ID
     * @return fluent ConversationSimulator
     */
    public ConversationSimulator user(long userId) {
        return user(userId, userId);
    }

    /**
     * Starts a fluent conversation simulation for the given user ID and chat ID.
     *
     * @param userId Telegram user ID
     * @param chatId Telegram chat ID
     * @return fluent ConversationSimulator
     */
    public ConversationSimulator user(long userId, long chatId) {
        User user = User.builder()
                .id(userId)
                .isBot(false)
                .firstName("User" + userId)
                .username("user" + userId)
                .build();
        Chat chat = Chat.builder()
                .id(chatId)
                .type(chatId == userId ? "private" : "group")
                .firstName("User" + userId)
                .username("user" + userId)
                .build();
        return new ConversationSimulator(this, user, chat);
    }

    /**
     * Starts a fluent conversation simulation with a fully customized user object.
     *
     * @param user Telegram user
     * @return fluent ConversationSimulator
     */
    public ConversationSimulator user(User user) {
        Chat chat = Chat.builder()
                .id(user.id())
                .type("private")
                .firstName(user.firstName())
                .username(user.username())
                .build();
        return new ConversationSimulator(this, user, chat);
    }

    /**
     * Starts a fluent conversation simulation with customized user and chat objects.
     *
     * @param user Telegram user
     * @param chat Telegram chat
     * @return fluent ConversationSimulator
     */
    public ConversationSimulator user(User user, Chat chat) {
        return new ConversationSimulator(this, user, chat);
    }

    public String getWebhookPath() {
        if (webhookPath != null) {
            return webhookPath;
        }
        try {
            return ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.telegram.webhook.path", String.class)
                    .orElse("/telegram/webhook");
        } catch (Exception e) {
            return "/telegram/webhook";
        }
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    public String getSecretToken() {
        if (secretToken != null) {
            return secretToken;
        }
        try {
            return ConfigProvider.getConfig()
                    .getOptionalValue("quarkus.telegram.webhook.secret-token", String.class)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
}
