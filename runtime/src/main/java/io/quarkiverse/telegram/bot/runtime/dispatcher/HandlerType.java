package io.quarkiverse.telegram.bot.runtime.dispatcher;

public enum HandlerType {
    COMMAND,
    CALLBACK_QUERY,
    MESSAGE,
    STATE_MISMATCH,
    INLINE_QUERY,
    CHOSEN_INLINE_RESULT,
    ERROR,
    BEFORE_UPDATE,
    AFTER_UPDATE
}
