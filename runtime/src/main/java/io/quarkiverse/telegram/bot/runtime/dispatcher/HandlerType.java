package io.quarkiverse.telegram.bot.runtime.dispatcher;

public enum HandlerType {
    COMMAND,
    CALLBACK_QUERY,
    MESSAGE,
    STATE_MISMATCH
}
