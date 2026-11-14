package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendVideoJsonRequest(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("video") String video,
        @JsonProperty("caption") String caption,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public SendVideoJsonRequest(Object chatId, String video, String caption, ReplyMarkup replyMarkup) {
        this(chatId, video, caption, null, replyMarkup);
    }
}
