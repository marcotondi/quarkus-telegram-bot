package io.quarkiverse.telegram.bot.runtime.client;

public class TelegramApiException extends RuntimeException {

    private final Integer errorCode;
    private final String description;
    private final Integer retryAfter;

    public TelegramApiException(Integer errorCode, String description) {
        this(errorCode, description, null);
    }

    public TelegramApiException(Integer errorCode, String description, Integer retryAfter) {
        super(formatMessage(errorCode, description));
        this.errorCode = errorCode;
        this.description = description;
        this.retryAfter = retryAfter;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
    }

    public Integer getRetryAfter() {
        return retryAfter;
    }

    private static String formatMessage(Integer errorCode, String description) {
        if (errorCode != null && description != null) {
            return "Telegram API error (" + errorCode + "): " + description;
        } else if (description != null) {
            return "Telegram API error: " + description;
        } else if (errorCode != null) {
            return "Telegram API error code: " + errorCode;
        }
        return "Unknown Telegram API error";
    }
}
