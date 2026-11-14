package io.quarkiverse.telegram.bot.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.telegram.bot.runtime.session.RedisSessionStore;
import io.quarkiverse.telegram.bot.runtime.session.SessionKey;
import io.quarkiverse.telegram.bot.runtime.session.SessionSnapshot;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
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

    @Test
    public void testAcquireAndReleaseDistributedLockWithToken() {
        SessionKey key = new SessionKey(1001, 2001);
        String lockKey = key.toStorageKey() + ":lock";
        String token = "custom-token-123";

        when(commands.setAndChanged(eq(lockKey), eq(token), any(SetArgs.class))).thenReturn(Uni.createFrom().item(true));
        when(ds.execute(eq("EVAL"), anyString(), eq("1"), eq(lockKey), eq(token)))
                .thenReturn(Uni.createFrom().nullItem());

        boolean locked = store.acquireLock(key, token, Duration.ofSeconds(10)).await().indefinitely();
        assertTrue(locked);
        verify(commands).setAndChanged(eq(lockKey), eq(token), any(SetArgs.class));

        store.releaseLock(key, token).await().indefinitely();
        verify(ds).execute(eq("EVAL"), anyString(), eq("1"), eq(lockKey), eq(token));
    }

    @Test
    public void testSaveSerializationFailurePropagates() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Simulated error") {
        });
        RedisSessionStore failingStore = new RedisSessionStore(ds, failingMapper);

        SessionKey key = new SessionKey(1001, 2001);
        SessionSnapshot snapshot = new SessionSnapshot("ACTIVE", "{}", "java.lang.Object");

        assertThrows(Exception.class, () -> failingStore.save(key, snapshot, Duration.ofMinutes(10)).await().indefinitely());
    }

    @Test
    public void testLoadDeserializationFailurePropagates() {
        SessionKey key = new SessionKey(1001, 2001);
        when(commands.get(key.toStorageKey())).thenReturn(Uni.createFrom().item("invalid-json{"));

        assertThrows(Exception.class, () -> store.load(key).await().indefinitely());
    }
}
