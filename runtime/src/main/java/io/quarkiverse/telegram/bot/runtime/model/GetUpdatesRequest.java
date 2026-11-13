package io.quarkiverse.telegram.bot.runtime.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetUpdatesRequest(
        @JsonProperty("offset") Long offset,
        @JsonProperty("limit") Integer limit,
        @JsonProperty("timeout") Integer timeout,
        @JsonProperty("allowed_updates") List<String> allowedUpdates) {

    public GetUpdatesRequest(Long offset, Integer limit, Integer timeout) {
        this(offset, limit, timeout, null);
    }
}
