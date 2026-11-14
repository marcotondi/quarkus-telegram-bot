package io.quarkiverse.telegram.bot.runtime.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class SessionKeyTest {

    @Test
    void testSessionKeyStorageKeyFormat() {
        SessionKey key = new SessionKey(1001L, 2002L);
        assertEquals("tg:session:1001:2002", key.toStorageKey());
    }

    @Test
    void testEqualityAndHashCode() {
        SessionKey key1 = new SessionKey(123L, 456L);
        SessionKey key2 = new SessionKey(123L, 456L);
        SessionKey key3 = new SessionKey(123L, 789L);

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
    }
}
