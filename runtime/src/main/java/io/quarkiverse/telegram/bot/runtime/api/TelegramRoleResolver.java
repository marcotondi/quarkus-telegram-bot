package io.quarkiverse.telegram.bot.runtime.api;

import java.util.Set;

import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.User;

/**
 * SPI interface to resolve security roles for a given Telegram user and chat.
 * Applications can provide a CDI bean implementing this interface to supply custom roles
 * (e.g. from a database or permissions system).
 */
public interface TelegramRoleResolver {

    /**
     * Returns the set of roles for the given user in the given chat.
     *
     * @param user Telegram user (may be null if sender not present)
     * @param chat Telegram chat (may be null)
     * @return set of roles, never null
     */
    Set<String> getRoles(User user, Chat chat);
}
