package io.quarkiverse.telegram.bot.runtime.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetWebhookRequest(
        @JsonProperty("url") String url,
        @JsonProperty("secret_token") String secretToken,
        @JsonProperty("max_connections") Integer maxConnections,
        @JsonProperty("allowed_updates") List<String> allowedUpdates) {

    public SetWebhookRequest(String url, String secretToken) {
        this(url, secretToken, null, null);
    }
}
