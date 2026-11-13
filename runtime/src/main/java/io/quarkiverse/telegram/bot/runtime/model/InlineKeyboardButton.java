package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InlineKeyboardButton(
        @JsonProperty("text") String text,
        @JsonProperty("url") String url,
        @JsonProperty("callback_data") String callbackData) {

    public static InlineKeyboardButton callbackData(String text, String callbackData) {
        return new InlineKeyboardButton(text, null, callbackData);
    }

    public static InlineKeyboardButton url(String text, String url) {
        return new InlineKeyboardButton(text, url, null);
    }
}
