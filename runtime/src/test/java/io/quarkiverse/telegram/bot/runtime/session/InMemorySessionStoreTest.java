package io.quarkiverse.telegram.bot.runtime.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class InMemorySessionStoreTest {

    private InMemorySessionStore store;
    private SessionKey key;

    @BeforeEach
    void setUp() {
        store = new InMemorySessionStore(null);
        key = new SessionKey(1001L, 2002L);
    }

    @Test
    void testSaveAndLoadSession() {
        SessionSnapshot snapshot = new SessionSnapshot("AWAITING_INPUT", "{\"step\":1,\"item\":\"film\"}", "java.util.Map");
        store.save(key, snapshot, Duration.ofMinutes(10)).await().indefinitely();

        Optional<SessionSnapshot> loaded = store.load(key).await().indefinitely();
        assertTrue(loaded.isPresent());
        assertEquals("AWAITING_INPUT", loaded.get().state());
        assertEquals("{\"step\":1,\"item\":\"film\"}", loaded.get().payloadJson());
        assertEquals("java.util.Map", loaded.get().payloadType());
    }

    @Test
    void testSessionTtlExpiration() throws InterruptedException {
        SessionSnapshot snapshot = new SessionSnapshot("TEMP_STATE", "{\"foo\":\"bar\"}", "java.util.Map");
        // Save with 50ms TTL
        store.save(key, snapshot, Duration.ofMillis(50)).await().indefinitely();

        Optional<SessionSnapshot> immediate = store.load(key).await().indefinitely();
        assertTrue(immediate.isPresent());

        // Wait past TTL
        Thread.sleep(80);

        Optional<SessionSnapshot> expired = store.load(key).await().indefinitely();
        assertTrue(expired.isEmpty(), "Session should expire after TTL");
    }

    @Test
    void testLockAcquisitionAndRelease() {
        Boolean lockedFirst = store.acquireLock(key, Duration.ofSeconds(2)).await().indefinitely();
        assertTrue(lockedFirst, "Lock attempt should succeed with default implementation");

        store.releaseLock(key).await().indefinitely();
    }

    @Test
    void testDeleteSession() {
        SessionSnapshot snapshot = new SessionSnapshot("ACTIVE", "{\"k\":\"v\"}", "java.util.Map");
        store.save(key, snapshot, Duration.ofMinutes(5)).await().indefinitely();
        assertTrue(store.load(key).await().indefinitely().isPresent());

        store.delete(key).await().indefinitely();

        Optional<SessionSnapshot> loaded = store.load(key).await().indefinitely();
        assertTrue(loaded.isEmpty(), "Session should be empty after deletion");
    }

    @Test
    void testCleanExpired() throws InterruptedException {
        SessionKey k1 = new SessionKey(1L, 1L);
        SessionKey k2 = new SessionKey(2L, 2L);

        store.save(k1, new SessionSnapshot("S1", null, null), Duration.ofMillis(40)).await().indefinitely();
        store.save(k2, new SessionSnapshot("S2", null, null), Duration.ofMinutes(5)).await().indefinitely();

        assertEquals(2, store.size());
        Thread.sleep(60);

        int evicted = store.cleanExpired();
        assertEquals(1, evicted);
        assertEquals(1, store.size());
        assertTrue(store.load(k2).await().indefinitely().isPresent());
    }

    @Test
    void testSessionIsolationBetweenUsers() {
        SessionKey keyUser1 = new SessionKey(1001L, 1L);
        SessionKey keyUser2 = new SessionKey(1001L, 2L);

        store.save(keyUser1, new SessionSnapshot("STATE_1", null, null), Duration.ofMinutes(5)).await().indefinitely();
        store.save(keyUser2, new SessionSnapshot("STATE_2", null, null), Duration.ofMinutes(5)).await().indefinitely();

        Optional<SessionSnapshot> s1 = store.load(keyUser1).await().indefinitely();
        Optional<SessionSnapshot> s2 = store.load(keyUser2).await().indefinitely();

        assertTrue(s1.isPresent());
        assertTrue(s2.isPresent());
        assertEquals("STATE_1", s1.get().state());
        assertEquals("STATE_2", s2.get().state());
    }
}
