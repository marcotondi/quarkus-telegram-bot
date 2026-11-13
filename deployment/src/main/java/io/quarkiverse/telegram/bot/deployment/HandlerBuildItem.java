package io.quarkiverse.telegram.bot.deployment;

import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerDescriptor;
import io.quarkus.builder.item.MultiBuildItem;

public final class HandlerBuildItem extends MultiBuildItem {

    private final HandlerDescriptor descriptor;

    public HandlerBuildItem(HandlerDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    public HandlerDescriptor getDescriptor() {
        return descriptor;
    }
}
