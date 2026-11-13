package io.quarkiverse.telegram.bot.runtime.transport;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;
import io.quarkiverse.telegram.bot.runtime.dispatcher.UpdateDispatcher;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class WebhookHandler implements Handler<RoutingContext> {

    private static final Logger LOG = Logger.getLogger(WebhookHandler.class);
    private static final String SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final UpdateDispatcher dispatcher;
    private final TelegramBotConfig config;
    private final ObjectMapper objectMapper;

    public WebhookHandler(UpdateDispatcher dispatcher, TelegramBotConfig config, ObjectMapper objectMapper) {
        this.dispatcher = dispatcher;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(RoutingContext ctx) {
        // Validate secret token if configured or required
        if (config.webhook().secretToken().isPresent()) {
            String expected = config.webhook().secretToken().get();
            String actual = ctx.request().getHeader(SECRET_TOKEN_HEADER);
            if (actual == null || !actual.equals(expected)) {
                LOG.warn("Rejected incoming Telegram webhook request: secret token mismatch.");
                ctx.response().setStatusCode(403).end("Forbidden");
                return;
            }
        } else if (config.webhook().requireSecretToken()) {
            LOG.warn("Rejected incoming Telegram webhook request: secret token required but not configured.");
            ctx.response().setStatusCode(403).end("Forbidden");
            return;
        }

        String body = ctx.body() != null ? ctx.body().asString() : null;
        if (body == null || body.isBlank()) {
            ctx.response().setStatusCode(200).end();
            return;
        }

        try {
            Update update = objectMapper.readValue(body, Update.class);
            if (dispatcher != null) {
                dispatcher.dispatch(update)
                        .subscribe().with(
                                v -> {
                                    if (!ctx.response().ended()) {
                                        ctx.response().setStatusCode(200).end();
                                    }
                                },
                                err -> {
                                    LOG.errorf(err, "Error dispatching webhook update %d: %s", update.updateId(),
                                            err.getMessage());
                                    if (!ctx.response().ended()) {
                                        ctx.response().setStatusCode(500).end("Internal Server Error");
                                    }
                                });
            } else {
                ctx.response().setStatusCode(200).end();
            }
        } catch (Exception e) {
            LOG.errorf(e, "Failed to parse incoming Telegram update JSON: %s", e.getMessage());
            ctx.response().setStatusCode(400).end("Invalid JSON");
        }
    }
}
