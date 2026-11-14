package io.quarkiverse.telegram.bot.runtime.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be executed before an incoming Update is dispatched to a handler.
 * Can be used for logging, custom metrics, rate limiting, or audit trails.
 *
 * <p>
 * Supported parameter types:
 * <ul>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.Update}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.User}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.Chat}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.Message}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.model.CallbackQuery}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.session.ConversationContext}</li>
 * <li>{@link io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient}</li>
 * </ul>
 *
 * <p>
 * Supported return types:
 * <ul>
 * <li>{@code void}</li>
 * <li>{@code boolean} (returning {@code false} aborts further dispatch of the update)</li>
 * <li>{@link io.smallrye.mutiny.Uni Uni&lt;Void&gt;}</li>
 * <li>{@link io.smallrye.mutiny.Uni Uni&lt;Boolean&gt;} (returning {@code false} aborts further dispatch)</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BeforeUpdate {

    /**
     * Execution order priority. Lower numbers execute first.
     */
    int priority() default 0;
}
