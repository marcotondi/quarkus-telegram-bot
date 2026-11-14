package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendChatAction(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("action") String action,
        @JsonProperty("message_thread_id") Integer messageThreadId,
        @JsonProperty("business_connection_id") String businessConnectionId) {

    public SendChatAction(long chatId, String action) {
        this((Object) chatId, action, null, null);
    }

    public SendChatAction(long chatId, ChatAction action) {
        this((Object) chatId, action != null ? action.value() : null, null, null);
    }

    public SendChatAction(String chatId, String action) {
        this((Object) chatId, action, null, null);
    }

    public SendChatAction(String chatId, ChatAction action) {
        this((Object) chatId, action != null ? action.value() : null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Object chatId;
        private String action;
        private Integer messageThreadId;
        private String businessConnectionId;

        public Builder chatId(long chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder chatId(String chatId) {
            this.chatId = chatId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder action(ChatAction action) {
            this.action = action != null ? action.value() : null;
            return this;
        }

        public Builder messageThreadId(Integer messageThreadId) {
            this.messageThreadId = messageThreadId;
            return this;
        }

        public Builder businessConnectionId(String businessConnectionId) {
            this.businessConnectionId = businessConnectionId;
            return this;
        }

        public SendChatAction build() {
            if (chatId == null) {
                throw new IllegalStateException("chatId is required");
            }
            if (action == null || action.isBlank()) {
                throw new IllegalStateException("action is required");
            }
            return new SendChatAction(chatId, action, messageThreadId, businessConnectionId);
        }
    }
}
