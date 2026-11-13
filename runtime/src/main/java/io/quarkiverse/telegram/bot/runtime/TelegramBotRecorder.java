package io.quarkiverse.telegram.bot.runtime;

import java.util.List;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerDescriptor;
import io.quarkiverse.telegram.bot.runtime.dispatcher.UpdateDispatcher;
import io.quarkiverse.telegram.bot.runtime.model.BotCommand;
import io.quarkiverse.telegram.bot.runtime.model.SetWebhookRequest;
import io.quarkiverse.telegram.bot.runtime.session.SessionStore;
import io.quarkiverse.telegram.bot.runtime.transport.PollingRunner;
import io.quarkiverse.telegram.bot.runtime.transport.WebhookHandler;
import io.quarkus.arc.Arc;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class TelegramBotRecorder {

    private static final Logger LOG = Logger.getLogger(TelegramBotRecorder.class);

    public RuntimeValue<UpdateDispatcher> createDispatcher(List<HandlerDescriptor> handlers) {
        TelegramBotClient client = Arc.container().instance(TelegramBotClient.class).get();
        TelegramBotConfig config = Arc.container().instance(TelegramBotConfig.class).get();
        SessionStore sessionStore = Arc.container().instance(SessionStore.class).get();

        UpdateDispatcher dispatcher = new UpdateDispatcher(handlers, client, config, sessionStore);
        return new RuntimeValue<>(dispatcher);
    }

    public void initTransports(
            RuntimeValue<UpdateDispatcher> dispatcherVal,
            LaunchMode launchMode,
            List<BotCommand> commands) {
        TelegramBotClient client = Arc.container().instance(TelegramBotClient.class).get();
        TelegramBotConfig config = Arc.container().instance(TelegramBotConfig.class).get();
        PollingRunner pollingRunner = Arc.container().instance(PollingRunner.class).get();

        UpdateDispatcher dispatcher = dispatcherVal.getValue();
        pollingRunner.setDispatcher(dispatcher);

        if (config.token().isEmpty() || config.token().get().isBlank()) {
            return;
        }

        // Register bot commands with Telegram if any @TelegramCommand annotations had descriptions
        if (commands != null && !commands.isEmpty()) {
            client.setMyCommands(commands)
                    .subscribe().with(
                            ok -> LOG.debugf("Registered %d bot commands with Telegram", commands.size()),
                            err -> LOG.warnf("Failed to register bot commands with Telegram: %s", err.getMessage()));
        }

        TelegramBotConfig.TransportMode mode = config.mode();
        boolean isWebhook = (mode == TelegramBotConfig.TransportMode.WEBHOOK)
                || (mode == TelegramBotConfig.TransportMode.AUTO && !launchMode.isDevOrTest());

        if (isWebhook) {
            // Auto register webhook if publicUrl is configured
            if (config.webhook().autoRegister() && config.webhook().publicUrl().isPresent()) {
                String fullWebhookUrl = config.webhook().publicUrl().get() + config.webhook().path();
                SetWebhookRequest req = new SetWebhookRequest(
                        fullWebhookUrl,
                        config.webhook().secretToken().orElse(null));
                client.setWebhook(req)
                        .subscribe().with(
                                ok -> LOG.infof("Registered Telegram Webhook at %s", fullWebhookUrl),
                                err -> LOG.errorf(err, "Failed to register Telegram Webhook at %s: %s", fullWebhookUrl,
                                        err.getMessage()));
            }
        } else {
            pollingRunner.startIfEnabled(launchMode);
        }
    }

    public Handler<RoutingContext> createWebhookHandler(RuntimeValue<UpdateDispatcher> dispatcherVal) {
        TelegramBotConfig config = Arc.container().instance(TelegramBotConfig.class).get();
        ObjectMapper objectMapper = Arc.container().instance(ObjectMapper.class).get();
        return new WebhookHandler(dispatcherVal.getValue(), config, objectMapper);
    }
}
