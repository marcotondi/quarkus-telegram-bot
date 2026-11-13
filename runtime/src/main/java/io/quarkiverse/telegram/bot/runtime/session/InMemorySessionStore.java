package io.quarkiverse.telegram.bot.runtime.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;

@ApplicationScoped
@DefaultBean
public class InMemorySessionStore implements SessionStore {

    private record Entry(SessionSnapshot snapshot, Instant expiresAt) {
    }

    private final ConcurrentHashMap<SessionKey, Entry> store = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private Long timerId;

    @Inject
    public InMemorySessionStore(Vertx vertx) {
        this.vertx = vertx;
    }

    void onStart(@Observes StartupEvent event) {
        if (vertx != null) {
            timerId = vertx.setPeriodic(60_000, id -> cleanExpired());
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        stopTimer();
    }

    @PreDestroy
    void destroy() {
        stopTimer();
    }

    private void stopTimer() {
        if (vertx != null && timerId != null) {
            vertx.cancelTimer(timerId);
            timerId = null;
        }
    }

    public int cleanExpired() {
        Instant now = Instant.now();
        int evicted = 0;
        var it = store.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now.isAfter(entry.getValue().expiresAt())) {
                it.remove();
                evicted++;
            }
        }
        return evicted;
    }

    public int size() {
        return store.size();
    }

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
