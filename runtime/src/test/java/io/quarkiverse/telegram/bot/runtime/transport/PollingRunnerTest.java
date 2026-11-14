package io.quarkiverse.telegram.bot.runtime.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.runtime.TelegramBotStatus;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.dispatcher.UpdateDispatcher;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.smallrye.mutiny.Uni;

public class PollingRunnerTest {

    @Test
    public void lastUpdateIdAdvancesEvenWhenDispatchFails() throws Exception {
        TelegramBotClient client = mock(TelegramBotClient.class);
        TelegramBotConfig config = mock(TelegramBotConfig.class);
        TelegramBotStatus status = new TelegramBotStatus();
        PollingRunner runner = new PollingRunner(client, config, status);

        var field = PollingRunner.class.getDeclaredField("running");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) field.get(runner)).set(true);

        UpdateDispatcher dispatcher = mock(UpdateDispatcher.class);
        Update failingUpdate = Update.builder().updateId(105L).build();
        Update nextUpdate = Update.builder().updateId(106L).build();

        when(dispatcher.dispatch(failingUpdate))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Simulated error")));
        when(dispatcher.dispatch(nextUpdate))
                .thenReturn(Uni.createFrom().voidItem());

        runner.setDispatcher(dispatcher);

        runner.processUpdatesSequentially(List.of(failingUpdate, nextUpdate)).await().indefinitely();

        assertEquals(106L, runner.getLastUpdateId());
    }
}
