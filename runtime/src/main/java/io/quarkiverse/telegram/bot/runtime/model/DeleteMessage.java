package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeleteMessage(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("message_id") long messageId) {

    public DeleteMessage(long chatId, long messageId) {
        this((Object) chatId, messageId);
    }

    public DeleteMessage(String chatId, long messageId) {
        this((Object) chatId, messageId);
    }
}
