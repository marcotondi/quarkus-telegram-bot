package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnCallbackQuery {

    /** Pattern or data matching callback query data, with optional wildcard e.g. "order_*" */
    String value() default "*";
}
