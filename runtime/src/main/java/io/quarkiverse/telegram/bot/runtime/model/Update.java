package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Update(
        @JsonProperty("update_id") long updateId,
        @JsonProperty("message") Message message,
        @JsonProperty("callback_query") CallbackQuery callbackQuery) {
}
