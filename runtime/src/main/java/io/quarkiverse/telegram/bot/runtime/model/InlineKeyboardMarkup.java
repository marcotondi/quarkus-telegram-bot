package io.quarkiverse.telegram.bot.runtime.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InlineKeyboardMarkup(
        @JsonProperty("inline_keyboard") List<List<InlineKeyboardButton>> inlineKeyboard) implements ReplyMarkup {

    public static InlineKeyboardMarkup singleColumn(InlineKeyboardButton... buttons) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (InlineKeyboardButton button : buttons) {
            rows.add(List.of(button));
        }
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup singleRow(InlineKeyboardButton... buttons) {
        return new InlineKeyboardMarkup(List.of(List.of(buttons)));
    }
}
