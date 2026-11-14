package io.quarkiverse.telegram.bot.runtime.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class TelegramSecurityExceptionTest {

    @Test
    void testExceptionFields() {
        TelegramSecurityException ex = new TelegramSecurityException("Unauthorized", 12345L, 67890L, "admin");

        assertEquals("Unauthorized", ex.getMessage());
        assertEquals(12345L, ex.getUserId());
        assertEquals(67890L, ex.getChatId());
        assertEquals("admin", ex.getRequiredRole());
    }

    @Test
    void testSingleArgConstructor() {
        TelegramSecurityException ex = new TelegramSecurityException("Access Denied");

        assertEquals("Access Denied", ex.getMessage());
        assertNull(ex.getUserId());
        assertNull(ex.getChatId());
        assertNull(ex.getRequiredRole());
    }
}
