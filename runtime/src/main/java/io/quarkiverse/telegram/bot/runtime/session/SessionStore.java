package io.quarkiverse.telegram.bot.runtime.session;

import java.time.Duration;
import java.util.Optional;

import io.smallrye.mutiny.Uni;

public interface SessionStore {

    Uni<Optional<SessionSnapshot>> load(SessionKey key);

    Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl);

    Uni<Void> delete(SessionKey key);

    default Uni<Boolean> acquireLock(SessionKey key, Duration timeout) {
        return Uni.createFrom().item(true);
    }

    default Uni<Void> releaseLock(SessionKey key) {
        return Uni.createFrom().voidItem();
    }
}
