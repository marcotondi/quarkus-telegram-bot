package io.quarkiverse.telegram.bot.runtime.session;

public record SessionKey(long chatId, long userId) {

    public String toStorageKey() {
        return "tg:session:" + chatId + ":" + userId;
    }
}
