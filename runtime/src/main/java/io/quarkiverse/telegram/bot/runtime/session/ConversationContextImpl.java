package io.quarkiverse.telegram.bot.runtime.session;

import java.time.Duration;
import java.util.Optional;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ConversationContextImpl implements ConversationContext {

    private static final Logger LOG = Logger.getLogger(ConversationContextImpl.class);

    private final SessionKey sessionKey;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    private String currentState;
    private Object currentPayload;
    private boolean completed = false;
    private boolean modified = false;

    public ConversationContextImpl(
            SessionKey sessionKey,
            SessionSnapshot snapshot,
            SessionStore sessionStore,
            ObjectMapper objectMapper,
            Duration ttl) {
        this.sessionKey = sessionKey;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
        this.ttl = ttl;

        if (snapshot != null) {
            this.currentState = snapshot.state();
            if (snapshot.payloadJson() != null && snapshot.payloadType() != null) {
                try {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    Class<?> clazz = (cl != null) ? cl.loadClass(snapshot.payloadType())
                            : Class.forName(snapshot.payloadType());
                    this.currentPayload = objectMapper.readValue(snapshot.payloadJson(), clazz);
                } catch (Exception e) {
                    LOG.warnf("Failed to deserialize session payload for %s: %s", sessionKey, e.getMessage());
                }
            }
        }
    }

    @Override
    public Optional<String> currentState() {
        return completed ? Optional.empty() : Optional.ofNullable(currentState);
    }

    @Override
    public <T> Optional<T> data(Class<T> type) {
        if (completed || currentPayload == null) {
            return Optional.empty();
        }
        if (type.isInstance(currentPayload)) {
            return Optional.of(type.cast(currentPayload));
        }
        try {
            return Optional.ofNullable(objectMapper.convertValue(currentPayload, type));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public void transitionTo(String nextState, Object data) {
        this.currentState = nextState;
        this.currentPayload = data;
        this.completed = false;
        this.modified = true;
    }

    @Override
    public void complete() {
        this.currentState = null;
        this.currentPayload = null;
        this.completed = true;
        this.modified = true;
    }

    public boolean isModified() {
        return modified;
    }

    public boolean isCompleted() {
        return completed;
    }

    public String getCurrentState() {
        return currentState;
    }

    public Object getCurrentPayload() {
        return currentPayload;
    }

    public SessionKey getSessionKey() {
        return sessionKey;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public Duration getTtl() {
        return ttl;
    }
}
