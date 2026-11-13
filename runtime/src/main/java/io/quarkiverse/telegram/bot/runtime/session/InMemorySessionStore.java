package io.quarkiverse.telegram.bot.runtime.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
@DefaultBean
public class InMemorySessionStore implements SessionStore {

    private record Entry(SessionSnapshot snapshot, Instant expiresAt) {
    }

    private final ConcurrentHashMap<SessionKey, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<SessionSnapshot>> load(SessionKey key) {
        Entry entry = store.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            store.remove(key);
            return Uni.createFrom().item(Optional.empty());
        }
        return Uni.createFrom().item(Optional.of(entry.snapshot()));
    }

    @Override
    public Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl) {
        store.put(key, new Entry(snapshot, Instant.now().plus(ttl)));
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> delete(SessionKey key) {
        store.remove(key);
        return Uni.createFrom().voidItem();
    }
}
