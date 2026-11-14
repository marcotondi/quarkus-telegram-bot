package io.quarkiverse.telegram.bot.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class RateLimitTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.telegram.rate-limit.enabled", "true",
                "quarkus.telegram.rate-limit.max-requests", "1",
                "quarkus.telegram.rate-limit.window", "5m",
                "quarkus.telegram.rate-limit.message", "Rate limit superato, attendi.");
    }
}
