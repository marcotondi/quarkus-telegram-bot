package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an error handler for exceptions thrown during update dispatching.
 * <p>
 * Supported parameter types:
 * <ul>
 * <li>{@link Throwable} (or any specific subtype matching the thrown exception)</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.Update}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.session.ConversationContext}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.User}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.Chat}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.Message}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient}</li>
 * </ul>
 * <p>
 * Supported return types:
 * <ul>
 * <li>{@code void} or {@code Uni<Void>}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.SendMessage} (or {@code Uni<SendMessage>})</li>
 * <li>{@link String} (or {@code Uni<String>}) to send a courtesy text reply to the chat</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.EditMessageText} (or {@code Uni<EditMessageText>})</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnError {

    /**
     * The exception class handled by this method. Defaults to {@link Throwable}.
     * Methods with more specific exception classes take precedence over more generic ones.
     */
    Class<? extends Throwable> value() default Throwable.class;
}
