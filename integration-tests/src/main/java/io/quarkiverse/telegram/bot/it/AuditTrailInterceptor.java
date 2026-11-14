package io.quarkiverse.telegram.bot.it;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.AfterUpdate;
import io.quarkiverse.telegram.bot.runtime.api.BeforeUpdate;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;

@ApplicationScoped
public class AuditTrailInterceptor {

    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    public static void clear() {
        EVENTS.clear();
    }

    @BeforeUpdate(priority = 10)
    public boolean checkBanned(Update update, User user) {
        if (user != null && "banned_user".equals(user.username())) {
            EVENTS.add("BANNED_BLOCKED");
            return false;
        }
        EVENTS.add("BEFORE_10");
        return true;
    }

    @BeforeUpdate(priority = 20)
    public void before20(Update update) {
        EVENTS.add("BEFORE_20");
    }

    @AfterUpdate(priority = 10)
    public void after10(Update update, Throwable error) {
        EVENTS.add("AFTER_10:" + (error != null ? error.getClass().getSimpleName() : "SUCCESS"));
    }

    @AfterUpdate(priority = 20)
    public void after20(Update update) {
        EVENTS.add("AFTER_20");
    }
}
