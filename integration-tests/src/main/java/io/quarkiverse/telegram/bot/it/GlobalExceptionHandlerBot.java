package io.quarkiverse.telegram.bot.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.OnError;

@ApplicationScoped
public class GlobalExceptionHandlerBot {

    @OnError(SampleBot.ScopedTestException.class)
    public String handleGlobalScoped(SampleBot.ScopedTestException ex) {
        return "Global: " + ex.getMessage();
    }
}
