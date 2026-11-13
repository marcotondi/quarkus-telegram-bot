package io.quarkiverse.telegram.bot.runtime.client;

public class TelegramApiException extends RuntimeException {

    private final Integer errorCode;
    private final String description;

    public TelegramApiException(Integer errorCode, String description) {
        super(formatMessage(errorCode, description));
        this.errorCode = errorCode;
        this.description = description;
    }

    public Integer getErrorCode() {
        return errorCode;
    }

    public String getDescription() {
        return description;
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
