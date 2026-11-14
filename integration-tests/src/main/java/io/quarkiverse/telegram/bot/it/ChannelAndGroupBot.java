package io.quarkiverse.telegram.bot.it;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.ChatType;
import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;

@ApplicationScoped
public class ChannelAndGroupBot {

    @ChatType(ChatType.Type.CHANNEL)
    @OnMessage(type = MessageType.TEXT)
    public SendMessage onChannelBroadcast(Message msg) {
        return SendMessage.builder()
                .chatId(msg.chat().id())
                .text("Broadcast in channel: " + msg.text())
                .build();
    }

    @ChatType({ ChatType.Type.GROUP, ChatType.Type.SUPERGROUP })
    @TelegramCommand(value = "/rules", description = "Regole del gruppo")
    public String groupRules() {
        return "Regole del gruppo: Rispetta i membri.";
    }

    @ChatType(ChatType.Type.PRIVATE)
    @TelegramCommand(value = "/secret", description = "Comando solo privato")
    public String privateOnly() {
        return "Segreto privato: 42";
    }
}
