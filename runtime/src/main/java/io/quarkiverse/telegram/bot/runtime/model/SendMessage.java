package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessage(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("text") String text,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public SendMessage(long chatId, String text) {
        this(chatId, text, null, null);
    }

    public SendMessage(String chatId, String text) {
        this(chatId, text, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Object chatId;
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

        public SendMessage build() {
            if (chatId == null) {
                throw new IllegalStateException("chatId is required");
            }
            if (text == null) {
                throw new IllegalStateException("text is required");
            }
            return new SendMessage(chatId, text, parseMode, replyMarkup);
        }
    }
}
