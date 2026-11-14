package io.quarkiverse.telegram.bot.runtime.api;

import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.session.ConversationContext;
import io.smallrye.mutiny.Uni;

/**
 * Interface implemented by CDI beans to globally handle exceptions
 * occurring during Telegram update dispatching.
 *
 * @param <T> Exception type handled
 */
public interface TelegramExceptionHandler<T extends Throwable> {

    /**
     * Handles the given exception and returns a reactive Uni.
     *
     * @param error the thrown error
     * @param update the incoming Telegram update
     * @param ctx conversation context (may be null if stateless)
     * @return Uni completing when error handling finishes
     */
    Uni<Void> handle(T error, Update update, ConversationContext ctx);
}
