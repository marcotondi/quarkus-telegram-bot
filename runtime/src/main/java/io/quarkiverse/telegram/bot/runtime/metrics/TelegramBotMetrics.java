package io.quarkiverse.telegram.bot.runtime.metrics;

import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer metrics for update processing, registered only when the Micrometer extension is on the classpath.
 */
@ApplicationScoped
public class TelegramBotMetrics {

    public static final String UPDATES_RECEIVED = "telegram.updates.received";
    public static final String UPDATES_DURATION = "telegram.updates.duration";
    public static final String UPDATES_ERRORS = "telegram.updates.errors";

    private final MeterRegistry registry;

    @Inject
    public TelegramBotMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void updateReceived(String type) {
        registry.counter(UPDATES_RECEIVED, "type", type).increment();
    }

    public void updateCompleted(String type, long durationNanos, Throwable error) {
        registry.timer(UPDATES_DURATION, "type", type).record(durationNanos, TimeUnit.NANOSECONDS);
        if (error != null) {
            registry.counter(UPDATES_ERRORS, "type", type, "exception", error.getClass().getSimpleName()).increment();
        }
    }
}
