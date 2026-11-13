package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendAudioJsonRequest(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("audio") String audio,
        @JsonProperty("caption") String caption,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public SendAudioJsonRequest(long chatId, String audio, String caption, ReplyMarkup replyMarkup) {
        this(chatId, audio, caption, null, replyMarkup);
    }
}
