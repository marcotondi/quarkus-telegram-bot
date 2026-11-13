package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReplyKeyboardRemove(
        @JsonProperty("remove_keyboard") boolean removeKeyboard,
        @JsonProperty("selective") Boolean selective) implements ReplyMarkup {

    public ReplyKeyboardRemove() {
        this(true, null);
    }
}
