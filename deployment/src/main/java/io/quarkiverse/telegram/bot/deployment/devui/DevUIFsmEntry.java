package io.quarkiverse.telegram.bot.deployment.devui;

public record DevUIFsmEntry(
        String state,
        String trigger,
        String handler,
        String interruptsState,
        String description) {
}
