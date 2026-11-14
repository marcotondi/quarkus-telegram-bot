package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditMessageText(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("inline_message_id") String inlineMessageId,
        @JsonProperty("text") String text,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public EditMessageText(long chatId, long messageId, String text) {
        this((Object) chatId, messageId, null, text, null, null);
    }

    public EditMessageText(long chatId, long messageId, String text, ReplyMarkup replyMarkup) {
        this((Object) chatId, messageId, null, text, null, replyMarkup);
    }

    public EditMessageText(String chatId, long messageId, String text) {
        this((Object) chatId, messageId, null, text, null, null);
    }

    public EditMessageText(String chatId, long messageId, String text, ReplyMarkup replyMarkup) {
        this((Object) chatId, messageId, null, text, null, replyMarkup);
    }

    public EditMessageText(String inlineMessageId, String text, ReplyMarkup replyMarkup) {
        this(null, null, inlineMessageId, text, null, replyMarkup);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Object chatId;
        private Long messageId;
        private String inlineMessageId;
        private String text;
        private String parseMode;
        private ReplyMarkup replyMarkup;

        public Builder chatId(long chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder messageId(long messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder inlineMessageId(String inlineMessageId) {
            this.inlineMessageId = inlineMessageId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder parseMode(String parseMode) {
            this.parseMode = parseMode;
            return this;
        }

        public Builder replyMarkup(ReplyMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
            return this;
        }

        public EditMessageText build() {
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("text is required for EditMessageText");
            }
            return new EditMessageText(chatId, messageId, inlineMessageId, text, parseMode, replyMarkup);
        }
    }
}
