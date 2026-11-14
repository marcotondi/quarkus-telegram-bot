package io.quarkiverse.telegram.bot.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.telegram.bot.runtime.api.TelegramExceptionHandler;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.session.ConversationContext;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class OrderExceptionHandler implements TelegramExceptionHandler<SampleBot.OrderException> {

    @Inject
    TelegramBotClient client;

    @Override
    public Uni<Void> handle(SampleBot.OrderException error, Update update, ConversationContext ctx) {
        Long chatId = update.message().chat().id();
        return client.sendMessage(chatId, "HandlerInterface: " + error.getMessage()).replaceWithVoid();
    }
}
