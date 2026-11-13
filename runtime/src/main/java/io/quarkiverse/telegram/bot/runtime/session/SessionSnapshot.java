package io.quarkiverse.telegram.bot.runtime.session;

import java.io.Serializable;

public record SessionSnapshot(String state, String payloadJson, String payloadType) implements Serializable {
}
