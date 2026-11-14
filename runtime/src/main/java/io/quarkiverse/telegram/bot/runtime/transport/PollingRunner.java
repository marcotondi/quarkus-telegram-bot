package io.quarkiverse.telegram.bot.runtime.transport;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.telegram.bot.runtime.TelegramBotStatus;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.dispatcher.UpdateDispatcher;
import io.quarkiverse.telegram.bot.runtime.model.GetUpdatesRequest;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.ShutdownEvent;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class PollingRunner {

    private static final Logger LOG = Logger.getLogger(PollingRunner.class);

    private final TelegramBotClient client;
    private final TelegramBotConfig config;
    private final TelegramBotStatus status;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastUpdateId = new AtomicLong(0);

    private UpdateDispatcher dispatcher;

    @Inject
    public PollingRunner(TelegramBotClient client, TelegramBotConfig config, TelegramBotStatus status) {
        this.client = client;
        this.config = config;
        this.status = status;
    }

    public void setDispatcher(UpdateDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void startIfEnabled(LaunchMode launchMode) {
        if (config.token().isEmpty() || config.token().get().isBlank()) {
            LOG.info("Telegram bot token not configured. Telegram bot transport is disabled.");
            return;
        }

        boolean shouldPoll = false;
        TelegramBotConfig.TransportMode mode = config.mode();
        if (mode == TelegramBotConfig.TransportMode.POLLING) {
            shouldPoll = true;
        } else if (mode == TelegramBotConfig.TransportMode.AUTO) {
            shouldPoll = launchMode.isDevOrTest();
        }

        if (!shouldPoll || !config.polling().enabled()) {
            return;
        }

        start();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            LOG.infof("Starting Telegram Long Polling engine (timeout: %s, limit: %d)...",
                    config.polling().timeout(), config.polling().limit());

            // Delete any existing webhook before polling, to prevent conflict
            client.deleteWebhook()
                    .onFailure().recoverWithItem(false)
                    .subscribe().with(deleted -> {
                        if (deleted) {
                            LOG.debug("Existing Telegram webhook deleted before starting polling.");
                        }
                        pollLoop();
                    });
        }
    }

    private void pollLoop() {
        if (!running.get()) {
            return;
        }

        int timeoutSec = (int) config.polling().timeout().toSeconds();
        int limit = config.polling().limit();
        Long offset = lastUpdateId.get() > 0 ? lastUpdateId.get() + 1 : null;

        GetUpdatesRequest req = new GetUpdatesRequest(offset, limit, timeoutSec);
        client.getUpdates(req)
                .onFailure().retry().withBackOff(Duration.ofSeconds(2), Duration.ofSeconds(10)).atMost(10)
                .flatMap(updates -> {
                    if (updates == null || updates.isEmpty() || !running.get()) {
                        return Uni.createFrom().voidItem();
                    }
                    return processUpdatesSequentially(updates);
                })
                .subscribe().with(
                        v -> {
                            status.clearError();
                            if (running.get()) {
                                pollLoop();
                            }
                        },
                        error -> {
                            status.setLastError("polling: " + error.getMessage());
                            LOG.errorf(error, "Error in Telegram polling loop: %s", error.getMessage());
                            if (running.get()) {
                                // Wait 3 seconds and retry
                                Uni.createFrom().nullItem()
                                        .onItem().delayIt().by(Duration.ofSeconds(3))
                                        .subscribe().with(ignored -> pollLoop());
                            }
                        });
    }

    private Uni<Void> processUpdatesSequentially(List<Update> updates) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (Update update : updates) {
            chain = chain.chain(() -> {
                if (!running.get()) {
                    return Uni.createFrom().voidItem();
                }
                if (dispatcher == null) {
                    if (update.updateId() > lastUpdateId.get()) {
                        lastUpdateId.set(update.updateId());
                    }
                    return Uni.createFrom().voidItem();
                }
                return dispatcher.dispatch(update)
                        .onItem().invoke(() -> {
                            if (update.updateId() > lastUpdateId.get()) {
                                lastUpdateId.set(update.updateId());
                            }
                        });
            });
        }
        return chain;
    }

    public boolean isRunning() {
        return running.get();
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping Telegram Long Polling engine...");
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        stop();
    }
}
