package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendAnimationJsonRequest(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("animation") String animation,
        @JsonProperty("caption") String caption,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public SendAnimationJsonRequest(long chatId, String animation, String caption, ReplyMarkup replyMarkup) {
        this((Object) chatId, animation, caption, null, replyMarkup);
    }

    public SendAnimationJsonRequest(String chatId, String animation, String caption, ReplyMarkup replyMarkup) {
        this((Object) chatId, animation, caption, null, replyMarkup);
    }
}
