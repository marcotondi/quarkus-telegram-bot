package io.quarkiverse.telegram.bot.it;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class RedisTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.redis.devservices.enabled", "true",
                "quarkus.redis.devservices.image-name", "docker.io/library/redis:7-alpine",
                "quarkus.telegram.session.store", "redis");
    }
}
