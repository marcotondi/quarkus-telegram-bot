package io.quarkiverse.telegram.bot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.telegram.bot.runtime.session.RedisSessionStore;
import io.quarkiverse.telegram.bot.runtime.session.SessionKey;
import io.quarkiverse.telegram.bot.runtime.session.SessionSnapshot;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;

public class RedisSessionStoreTest {

    private ReactiveRedisDataSource ds;
    private ReactiveValueCommands<String, String> commands;
    private ObjectMapper mapper;
    private RedisSessionStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setup() {
        ds = mock(ReactiveRedisDataSource.class);
        commands = mock(ReactiveValueCommands.class);
        when(ds.value(String.class)).thenReturn(commands);
        mapper = new ObjectMapper();
        store = new RedisSessionStore(ds, mapper);
    }

    @Test
    public void testLoadExistingSession() {
        SessionKey key = new SessionKey(1001, 2001);
        String json = "{\"state\":\"WAITING_INPUT\",\"payloadJson\":\"{\\\"foo\\\":\\\"bar\\\"}\",\"payloadType\":\"java.util.Map\"}";
        when(commands.get(key.toStorageKey())).thenReturn(Uni.createFrom().item(json));

        Optional<SessionSnapshot> snapshot = store.load(key).await().indefinitely();

        assertTrue(snapshot.isPresent());
        assertEquals("WAITING_INPUT", snapshot.get().state());
        assertEquals("java.util.Map", snapshot.get().payloadType());
        verify(commands).get(key.toStorageKey());
    }

    @Test
    public void testLoadMissingSession() {
        SessionKey key = new SessionKey(1001, 2001);
        when(commands.get(key.toStorageKey())).thenReturn(Uni.createFrom().nullItem());

        Optional<SessionSnapshot> snapshot = store.load(key).await().indefinitely();

        assertTrue(snapshot.isEmpty());
    }

    @Test
    public void testSaveSession() {
        SessionKey key = new SessionKey(1001, 2001);
        SessionSnapshot snapshot = new SessionSnapshot("ACTIVE", "{}", "java.lang.Object");
        when(commands.psetex(eq(key.toStorageKey()), anyLong(), anyString())).thenReturn(Uni.createFrom().voidItem());

        store.save(key, snapshot, Duration.ofMinutes(10)).await().indefinitely();

        verify(commands).psetex(eq(key.toStorageKey()), eq(600_000L), anyString());
    }

    @Test
    public void testDeleteSession() {
        SessionKey key = new SessionKey(1001, 2001);
        when(commands.getdel(key.toStorageKey())).thenReturn(Uni.createFrom().item("old_val"));

        store.delete(key).await().indefinitely();

        verify(commands).getdel(key.toStorageKey());
    }
}
