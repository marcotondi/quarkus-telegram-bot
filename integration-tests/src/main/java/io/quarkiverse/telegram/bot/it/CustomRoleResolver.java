package io.quarkiverse.telegram.bot.it;

import java.util.Collections;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.TelegramRoleResolver;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.User;

@ApplicationScoped
public class CustomRoleResolver implements TelegramRoleResolver {

    @Override
    public Set<String> getRoles(User user, Chat chat) {
        if (user != null && user.id() == 8888888L) {
            return Set.of("vip");
        }
        return Collections.emptySet();
    }
}
