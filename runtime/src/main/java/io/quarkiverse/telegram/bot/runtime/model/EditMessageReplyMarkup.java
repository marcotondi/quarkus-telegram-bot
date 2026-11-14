package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditMessageReplyMarkup(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("inline_message_id") String inlineMessageId,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public EditMessageReplyMarkup(long chatId, long messageId, ReplyMarkup replyMarkup) {
        this((Object) chatId, messageId, null, replyMarkup);
    }

    public EditMessageReplyMarkup(long chatId, long messageId) {
        this((Object) chatId, messageId, null, null);
    }

    public EditMessageReplyMarkup(String chatId, long messageId, ReplyMarkup replyMarkup) {
        this((Object) chatId, messageId, null, replyMarkup);
    }

    public EditMessageReplyMarkup(String chatId, long messageId) {
        this((Object) chatId, messageId, null, null);
    }

    public EditMessageReplyMarkup(String inlineMessageId, ReplyMarkup replyMarkup) {
        this(null, null, inlineMessageId, replyMarkup);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Object chatId;
        private Long messageId;
        private String inlineMessageId;
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

        public Builder replyMarkup(ReplyMarkup replyMarkup) {
            this.replyMarkup = replyMarkup;
            return this;
        }

        public EditMessageReplyMarkup build() {
            if (inlineMessageId == null && (chatId == null || messageId == null)) {
                throw new IllegalStateException("Either inlineMessageId or both (chatId and messageId) must be provided");
            }
            return new EditMessageReplyMarkup(chatId, messageId, inlineMessageId, replyMarkup);
        }
    }
}
