package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendVoiceJsonRequest(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("voice") String voice,
        @JsonProperty("caption") String caption,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public SendVoiceJsonRequest(long chatId, String voice, String caption, ReplyMarkup replyMarkup) {
        this((Object) chatId, voice, caption, null, replyMarkup);
    }

    public SendVoiceJsonRequest(String chatId, String voice, String caption, ReplyMarkup replyMarkup) {
        this((Object) chatId, voice, caption, null, replyMarkup);
    }
}
