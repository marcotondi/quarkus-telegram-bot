package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendPhotoJsonRequest(
        @JsonProperty("chat_id") Object chatId,
        @JsonProperty("photo") String photo,
        @JsonProperty("caption") String caption,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("reply_markup") ReplyMarkup replyMarkup) {

    public SendPhotoJsonRequest(long chatId, String photo, String caption, ReplyMarkup replyMarkup) {
        this(chatId, photo, caption, null, replyMarkup);
    }
}
