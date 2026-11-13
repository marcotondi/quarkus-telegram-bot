package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TelegramCommand {

    /** The command string (e.g. "/start" or "start") */
    String value();

    /** Description of the command, registered on Telegram via setMyCommands */
    String description() default "";

    /** If true, clears any active conversation state when received */
    boolean interruptsState() default false;
}
