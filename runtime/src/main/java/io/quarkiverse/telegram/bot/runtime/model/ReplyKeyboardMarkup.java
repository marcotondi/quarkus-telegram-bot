package io.quarkiverse.telegram.bot.runtime.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplyKeyboardMarkup(
        @JsonProperty("keyboard") List<List<KeyboardButton>> keyboard,
        @JsonProperty("is_persistent") Boolean isPersistent,
        @JsonProperty("resize_keyboard") Boolean resizeKeyboard,
        @JsonProperty("one_time_keyboard") Boolean oneTimeKeyboard) implements ReplyMarkup {
}
