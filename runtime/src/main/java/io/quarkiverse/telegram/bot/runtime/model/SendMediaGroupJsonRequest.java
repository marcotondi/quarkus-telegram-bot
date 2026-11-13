package io.quarkiverse.telegram.bot.runtime.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMediaGroupJsonRequest(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("media") List<? extends InputMedia> media) {

    public SendMediaGroupJsonRequest(long chatId, List<? extends InputMedia> media) {
        this((Object) chatId, media);
    }
}
