package io.quarkiverse.telegram.bot.runtime.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory fixed-window rate limiter keyed by user or chat id.
 * <p>
 * Note: this counter is maintained in-process per JVM instance. In multi-pod deployments,
 * the effective cluster-wide rate limit is N * maxRequests. If strict global limits
 * across multiple instances are required, configure rate limiting at the ingress or reverse proxy layer.
 */
public final class RateLimiter {

    public enum Decision {
        /** Request is within the limit. */
        ALLOWED,
        /** Limit exceeded: the rejection message should be sent to the user. */
        LIMITED,
        /** Limit exceeded again in the same window: stay silent to avoid amplifying the spam. */
        LIMITED_SILENT
    }

    private static final int PURGE_THRESHOLD = 10_000;

    private final int maxRequests;
    private final long windowNanos;
    private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, Duration window) {
        this.maxRequests = Math.max(1, maxRequests);
        this.windowNanos = Math.max(1, window.toNanos());
    }

    public Decision check(long key) {
        long now = System.nanoTime();
        Window window = windows.compute(key, (k, current) -> current == null || now - current.startNanos >= windowNanos
                ? new Window(now)
                : current);
        if (window.count.incrementAndGet() <= maxRequests) {
            return Decision.ALLOWED;
        }
        return window.notified.compareAndSet(false, true) ? Decision.LIMITED : Decision.LIMITED_SILENT;
    }

    /** Drops expired windows once the map grows past the threshold. */
    public void purgeExpired() {
        long now = System.nanoTime();
        if (windows.size() > PURGE_THRESHOLD) {
            windows.entrySet().removeIf(e -> now - e.getValue().startNanos >= windowNanos);
        }
    }

    private record Window(long startNanos, AtomicInteger count, AtomicBoolean notified) {
        Window(long startNanos) {
            this(startNanos, new AtomicInteger(), new AtomicBoolean());
        }
    }
}
