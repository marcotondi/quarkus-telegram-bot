package io.quarkiverse.telegram.bot.runtime;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Runtime state of the active Telegram transport, updated at startup by the recorder and by the transport implementations.
 * Used by the optional health check.
 */
@ApplicationScoped
public class TelegramBotStatus {

    public enum Transport {
        NONE,
        POLLING,
        WEBHOOK
    }

    private volatile Transport transport = Transport.NONE;
    private volatile String lastError;

    public Transport getTransport() {
        return transport;
    }

    public void setTransport(Transport transport) {
        this.transport = transport;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void clearError() {
        this.lastError = null;
    }

    public boolean hasError() {
        return lastError != null;
    }
}
