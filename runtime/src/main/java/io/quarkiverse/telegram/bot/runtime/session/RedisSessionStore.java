package io.quarkiverse.telegram.bot.runtime.session;

import java.time.Duration;
import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.value.ReactiveValueCommands;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
@IfBuildProperty(name = "quarkus.telegram.session.store", stringValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final Logger LOG = Logger.getLogger(RedisSessionStore.class);

    private final ReactiveValueCommands<String, String> commands;
    private final ObjectMapper mapper;

    @Inject
    public RedisSessionStore(ReactiveRedisDataSource ds, ObjectMapper mapper) {
        this.commands = ds.value(String.class);
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<SessionSnapshot>> load(SessionKey key) {
        return commands.get(key.toStorageKey())
                .map(raw -> raw == null ? Optional.empty() : Optional.ofNullable(deserialize(raw)));
    }

    @Override
    public Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl) {
        String json = serialize(snapshot);
        if (json == null) {
            return Uni.createFrom().voidItem();
        }
        return commands.psetex(key.toStorageKey(), ttl.toMillis(), json);
    }

    @Override
    public Uni<Void> delete(SessionKey key) {
        return commands.getdel(key.toStorageKey()).replaceWithVoid();
    }

    private String serialize(SessionSnapshot snapshot) {
        try {
            return mapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize SessionSnapshot to JSON for Redis");
            return null;
        }
    }

    private SessionSnapshot deserialize(String json) {
        try {
            return mapper.readValue(json, SessionSnapshot.class);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to deserialize SessionSnapshot from Redis: %s", json);
            return null;
        }
    }
}
