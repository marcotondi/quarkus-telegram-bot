package io.quarkiverse.telegram.bot.runtime.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ConversationContextTest {

    private ObjectMapper objectMapper;
    private SessionKey sessionKey;

    public record BookingData(String movie, int tickets) {
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        sessionKey = new SessionKey(101L, 202L);
    }

    @Test
    void testInitialEmptyContext() {
        ConversationContextImpl ctx = new ConversationContextImpl(
                sessionKey, null, null, objectMapper, Duration.ofMinutes(15));

        assertTrue(ctx.currentState().isEmpty());
        assertTrue(ctx.data(BookingData.class).isEmpty());
        assertFalse(ctx.isModified());
        assertFalse(ctx.isCompleted());
    }

    @Test
    void testTransitionToUpdatesStateAndData() {
        ConversationContextImpl ctx = new ConversationContextImpl(
                sessionKey, null, null, objectMapper, Duration.ofMinutes(15));

        BookingData data = new BookingData("Inception", 2);
        ctx.transitionTo("CHOOSE_SEATS", data);

        assertTrue(ctx.isModified());
        assertEquals("CHOOSE_SEATS", ctx.currentState().orElse(null));
        Optional<BookingData> retrieved = ctx.data(BookingData.class);
        assertTrue(retrieved.isPresent());
        assertEquals("Inception", retrieved.get().movie());
        assertEquals(2, retrieved.get().tickets());
    }

    @Test
    void testCompleteClearsStateAndMarksCompleted() {
        SessionSnapshot initial = new SessionSnapshot("ACTIVE", "{\"movie\":\"Interstellar\",\"tickets\":1}",
                BookingData.class.getName());
        ConversationContextImpl ctx = new ConversationContextImpl(
                sessionKey, initial, null, objectMapper, Duration.ofMinutes(15));

        assertEquals("ACTIVE", ctx.currentState().orElse(null));
        assertTrue(ctx.data(BookingData.class).isPresent());

        ctx.complete();

        assertTrue(ctx.isModified());
        assertTrue(ctx.isCompleted());
        assertTrue(ctx.currentState().isEmpty());
        assertTrue(ctx.data(BookingData.class).isEmpty());
    }

    @Test
    void testDeserializationFromSnapshot() {
        String json = "{\"movie\":\"Tenet\",\"tickets\":4}";
        SessionSnapshot snapshot = new SessionSnapshot("SELECT_TIME", json, BookingData.class.getName());

        ConversationContextImpl ctx = new ConversationContextImpl(
                sessionKey, snapshot, null, objectMapper, Duration.ofMinutes(15));

        assertEquals("SELECT_TIME", ctx.currentState().orElse(null));
        Optional<BookingData> data = ctx.data(BookingData.class);
        assertTrue(data.isPresent());
        assertEquals("Tenet", data.get().movie());
        assertEquals(4, data.get().tickets());
        assertFalse(ctx.isModified());
    }
}
