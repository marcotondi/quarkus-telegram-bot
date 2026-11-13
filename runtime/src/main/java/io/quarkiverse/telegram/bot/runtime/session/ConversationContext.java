package io.quarkiverse.telegram.bot.runtime.session;

import java.util.Optional;

public interface ConversationContext {

    Optional<String> currentState();

    <T> Optional<T> data(Class<T> type);

    void transitionTo(String nextState, Object data);

    default void transitionTo(String nextState) {
        transitionTo(nextState, null);
    }

    void complete();
}
