package io.quarkiverse.telegram.bot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.runtime.session.RedisSessionStore;
import io.quarkiverse.telegram.bot.runtime.session.SessionKey;
import io.quarkiverse.telegram.bot.runtime.session.SessionSnapshot;
import io.quarkiverse.telegram.bot.runtime.session.SessionStore;
import io.quarkiverse.telegram.bot.test.TelegramBotTestResource;
import io.quarkiverse.telegram.bot.test.TelegramBotTestSupport;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(RedisTestProfile.class)
@QuarkusTestResource(TelegramBotTestResource.class)
public class TelegramRedisDevServicesTest {

    @Inject
    SessionStore sessionStore;

    @Inject
    ReactiveRedisDataSource redisDataSource;

    @Inject
    TelegramBotTestSupport botTester;

    @Test
    public void testRedisSessionStoreActive() {
        assertInstanceOf(RedisSessionStore.class, sessionStore);
    }

    @Test
    public void testDirectRedisOperations() {
        SessionKey key = new SessionKey(9501L, 9502L);
        SessionSnapshot snapshot = new SessionSnapshot("ACTIVE_STEP", "{\"custom\":\"value\"}", "java.util.Map");

        sessionStore.save(key, snapshot, Duration.ofMinutes(5)).await().indefinitely();

        Optional<SessionSnapshot> loaded = sessionStore.load(key).await().indefinitely();
        assertTrue(loaded.isPresent());
        assertEquals("ACTIVE_STEP", loaded.get().state());
        assertEquals("java.util.Map", loaded.get().payloadType());

        // Verify key exists directly in Redis container
        String rawRedisValue = redisDataSource.value(String.class).get(key.toStorageKey()).await().indefinitely();
        assertNotNull(rawRedisValue);
        assertTrue(rawRedisValue.contains("ACTIVE_STEP"));

        // Distributed lock
        boolean locked = sessionStore.acquireLock(key, Duration.ofSeconds(10)).await().indefinitely();
        assertTrue(locked);
        sessionStore.releaseLock(key).await().indefinitely();

        // Delete
        sessionStore.delete(key).await().indefinitely();
        assertTrue(sessionStore.load(key).await().indefinitely().isEmpty());
    }

    @Test
    public void testFsmConversationFlowWithRedisPersistence() {
        long userId = 9999L;

        // User starts booking - state WAITING_DATE is persisted to Redis
        botTester.user(userId)
                .sendsCommand("/prenota")
                .assertBotReplied("Per quale data vuoi prenotare?");

        SessionKey key = new SessionKey(userId, userId);
        Optional<SessionSnapshot> snapshot = sessionStore.load(key).await().indefinitely();
        assertTrue(snapshot.isPresent());
        assertEquals("WAITING_DATE", snapshot.get().state());

        // User sends date -> state transitions to WAITING_GUESTS in Redis
        botTester.user(userId)
                .sendsMessage("2026-12-31")
                .assertBotReplied("Data registrata: 2026-12-31. Quanti ospiti sarete?");

        snapshot = sessionStore.load(key).await().indefinitely();
        assertTrue(snapshot.isPresent());
        assertEquals("WAITING_GUESTS", snapshot.get().state());

        // User confirms guests -> booking finished, session cleared from Redis
        botTester.user(userId)
                .clicksButton("guests_2")
                .assertBotReplied("Prenotazione confermata per 2 ospiti il 2026-12-31!");

        snapshot = sessionStore.load(key).await().indefinitely();
        assertTrue(snapshot.isEmpty());
    }
}
