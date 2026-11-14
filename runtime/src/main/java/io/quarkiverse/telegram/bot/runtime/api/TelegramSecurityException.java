package io.quarkiverse.telegram.bot.runtime.api;

/**
 * Thrown when an update sender does not satisfy the security requirements
 * specified by {@link SecuredBot} or {@link BotAdmin}.
 */
public class TelegramSecurityException extends SecurityException {

    private final Long userId;
    private final Long chatId;
    private final String requiredRole;

    public TelegramSecurityException(String message) {
        this(message, null, null, null);
    }

    public TelegramSecurityException(String message, Long userId, Long chatId, String requiredRole) {
        super(message);
        this.userId = userId;
        this.chatId = chatId;
        this.requiredRole = requiredRole;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getChatId() {
        return chatId;
    }

    public String getRequiredRole() {
        return requiredRole;
    }
}
