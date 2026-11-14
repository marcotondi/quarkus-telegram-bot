package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience annotation marking a Telegram bot handler (or all handlers in a class)
 * as restricted to bot administrators only (as configured in {@code quarkus.telegram.security.admin-ids}
 * or via {@link TelegramRoleResolver}).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BotAdmin {

    /**
     * If true, unauthorized updates are silently ignored without throwing an exception.
     * If false (default), a {@link TelegramSecurityException} is thrown.
     */
    boolean silent() default false;
}
