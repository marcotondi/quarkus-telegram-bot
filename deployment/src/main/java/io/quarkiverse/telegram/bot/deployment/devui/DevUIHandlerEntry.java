package io.quarkiverse.telegram.bot.deployment.devui;

public record DevUIHandlerEntry(
        String type,
        String match,
        String targetMethod,
        String chatTypes,
        String security,
        String state,
        String description) {
}
