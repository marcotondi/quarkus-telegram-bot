package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for incoming Telegram inline queries.
 * When a user types {@code @bot query} in any chat, Telegram sends an InlineQuery update.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnInlineQuery {

    /**
     * Pattern or prefix wildcard matching the inline query text.
     * Defaults to "*" (matches any query). E.g. "search_*" matches queries starting with "search_".
     */
    String value() default "*";
}
