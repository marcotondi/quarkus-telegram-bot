package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(InlineKeyboardMarkup.class),
        @JsonSubTypes.Type(ReplyKeyboardMarkup.class),
        @JsonSubTypes.Type(ReplyKeyboardRemove.class),
        @JsonSubTypes.Type(ForceReply.class)
})
public interface ReplyMarkup {
}
