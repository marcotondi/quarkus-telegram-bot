package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a Telegram bot handler method (or all handlers in a class)
 * to specific Telegram chat types (e.g. private, group, supergroup, channel).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ChatType {

    /**
     * The allowed chat types for this handler or class.
     */
    Type[] value() default { Type.PRIVATE };

    enum Type {
        PRIVATE("private"),
        GROUP("group"),
        SUPERGROUP("supergroup"),
        CHANNEL("channel");

        private final String value;

        Type(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Type fromString(String str) {
            if (str == null) {
                return null;
            }
            for (Type t : values()) {
                if (t.value.equalsIgnoreCase(str)) {
                    return t;
                }
            }
            return null;
        }
    }
}
