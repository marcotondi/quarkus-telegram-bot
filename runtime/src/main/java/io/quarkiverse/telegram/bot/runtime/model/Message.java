package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
        @JsonProperty("message_id") long messageId,
        @JsonProperty("date") Long date,
        @JsonProperty("chat") Chat chat,
        @JsonProperty("from") User from,
        @JsonProperty("text") String text) {
}
