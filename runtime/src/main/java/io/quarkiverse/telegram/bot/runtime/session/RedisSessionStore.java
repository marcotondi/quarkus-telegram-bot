package io.quarkiverse.telegram.bot.runtime.session;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
@IfBuildProperty(name = "quarkus.telegram.session.store", stringValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final Logger LOG = Logger.getLogger(RedisSessionStore.class);
    private static final String RELEASE_LOCK_SCRIPT = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final ReactiveRedisDataSource ds;
    private final ReactiveValueCommands<String, String> commands;
    private final ObjectMapper mapper;

    @Inject
    public RedisSessionStore(ReactiveRedisDataSource ds, ObjectMapper mapper) {
        this.ds = ds;
        this.commands = ds.value(String.class);
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<SessionSnapshot>> load(SessionKey key) {
        return commands.get(key.toStorageKey())
                .flatMap(raw -> {
                    if (raw == null) {
                        return Uni.createFrom().item(Optional.empty());
                    }
                    try {
                        SessionSnapshot snapshot = mapper.readValue(raw, SessionSnapshot.class);
                        return Uni.createFrom().item(Optional.ofNullable(snapshot));
                    } catch (Exception e) {
                        LOG.errorf(e, "Failed to deserialize SessionSnapshot from Redis for key=%s", key);
                        return Uni.createFrom().failure(e);
                    }
                });
    }

    @Override
    public Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl) {
        try {
            String json = mapper.writeValueAsString(snapshot);
            return commands.psetex(key.toStorageKey(), ttl.toMillis(), json);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to serialize SessionSnapshot to JSON for Redis (key=%s)", key);
            return Uni.createFrom().failure(e);
        }
    }

    @Override
    public Uni<Void> delete(SessionKey key) {
        return commands.getdel(key.toStorageKey()).replaceWithVoid();
    }

    @Override
    public Uni<Boolean> acquireLock(SessionKey key, String token, Duration timeout) {
        String lockKey = key.toStorageKey() + ":lock";
        return commands.setAndChanged(lockKey, token, new SetArgs().nx().px(timeout.toMillis()));
    }

    @Override
    public Uni<Boolean> acquireLock(SessionKey key, Duration timeout) {
        return acquireLock(key, UUID.randomUUID().toString(), timeout);
    }

    @Override
    public Uni<Void> releaseLock(SessionKey key, String token) {
        if (token == null) {
            return Uni.createFrom().voidItem();
        }
        String lockKey = key.toStorageKey() + ":lock";
        return ds.execute("EVAL", RELEASE_LOCK_SCRIPT, "1", lockKey, token).replaceWithVoid();
    }

    @Override
    public Uni<Void> releaseLock(SessionKey key) {
        String lockKey = key.toStorageKey() + ":lock";
        return commands.getdel(lockKey).replaceWithVoid();
    }
}
