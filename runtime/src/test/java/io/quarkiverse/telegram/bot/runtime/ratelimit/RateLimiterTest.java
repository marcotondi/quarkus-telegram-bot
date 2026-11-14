package io.quarkiverse.telegram.bot.runtime.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.runtime.ratelimit.RateLimiter.Decision;

public class RateLimiterTest {

    @Test
    void testAllowsUpToLimitThenStaysSilent() {
        RateLimiter limiter = new RateLimiter(2, Duration.ofMinutes(1));

        assertEquals(Decision.ALLOWED, limiter.check(1L));
        assertEquals(Decision.ALLOWED, limiter.check(1L));
        assertEquals(Decision.LIMITED, limiter.check(1L));
        assertEquals(Decision.LIMITED_SILENT, limiter.check(1L));
        assertEquals(Decision.ALLOWED, limiter.check(2L), "keys are tracked independently");
    }

    @Test
    void testWindowResetRestoresQuota() throws InterruptedException {
        RateLimiter limiter = new RateLimiter(1, Duration.ofMillis(50));

        assertEquals(Decision.ALLOWED, limiter.check(1L));
        assertEquals(Decision.LIMITED, limiter.check(1L));

        Thread.sleep(80);

        assertEquals(Decision.ALLOWED, limiter.check(1L));
        assertEquals(Decision.LIMITED, limiter.check(1L), "notification resets with the window");
    }
}
