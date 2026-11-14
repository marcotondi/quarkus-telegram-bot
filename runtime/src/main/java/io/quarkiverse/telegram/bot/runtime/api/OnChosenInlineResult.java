package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for Telegram ChosenInlineResult updates.
 * Sent when a user picks a result from an inline query and sends it to a chat.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnChosenInlineResult {

    /**
     * Pattern or prefix wildcard matching the result_id of the chosen inline result.
     * Defaults to "*" (matches any chosen result).
     */
    String value() default "*";
}
