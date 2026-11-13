package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CallbackQuery(
        @JsonProperty("id") String id,
        @JsonProperty("from") User from,
        @JsonProperty("message") Message message,
        @JsonProperty("inline_message_id") String inlineMessageId,
        @JsonProperty("chat_instance") String chatInstance,
        @JsonProperty("data") String data,
        @JsonProperty("game_short_name") String gameShortName) {
}
