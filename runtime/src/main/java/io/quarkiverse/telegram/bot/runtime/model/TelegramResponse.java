package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TelegramResponse<T>(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("result") T result,
        @JsonProperty("description") String description,
        @JsonProperty("error_code") Integer errorCode) {
}
