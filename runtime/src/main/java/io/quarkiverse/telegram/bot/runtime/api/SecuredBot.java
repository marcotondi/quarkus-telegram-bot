package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts access to a Telegram bot handler (or all handlers in a class)
 * based on user roles and/or allowed user identifiers.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SecuredBot {

    /**
     * Required roles (e.g. "admin", "moderator").
     * The sender must possess at least one of these roles (OR logic).
     * If empty, role checking is skipped.
     */
    String[] roles() default {};

    /**
     * Allowed numeric user IDs (e.g. "12345678").
     * <p>
     * Note: while usernames (e.g. "john" or "@john") are supported for backward compatibility,
     * numeric user IDs are strongly recommended because Telegram usernames are mutable and can be
     * transferred or claimed by other users.
     * If empty, user ID checking is skipped.
     */
    String[] allowedUsers() default {};

    /**
     * If true, unauthorized updates are silently ignored without throwing an exception.
     * If false (default), a {@link TelegramSecurityException} is thrown.
     */
    boolean silent() default false;
}
