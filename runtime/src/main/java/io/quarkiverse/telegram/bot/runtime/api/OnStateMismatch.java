package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be invoked when an update arrives while the session is in an active state,
 * but no matching handler was found for that state and input type.
 * <p>
 * Invoking this handler does not modify the conversational state.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnStateMismatch {

    /**
     * Optional state name. If specified, only matches when the conversation is in this specific state.
     * If left empty (default), acts as a general fallback for any unmatched state.
     */
    String value() default "";
}
