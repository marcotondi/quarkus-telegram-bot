package io.quarkiverse.telegram.bot.runtime.model;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Result of an editMessageText request.
 * Telegram returns a Message object when editing chat messages, or boolean true when editing inline messages.
 */
@JsonDeserialize(using = EditMessageResult.Deserializer.class)
public sealed interface EditMessageResult permits EditMessageResult.EditedMessage, EditMessageResult.InlineSuccess {

    record EditedMessage(Message message) implements EditMessageResult {
    }

    record InlineSuccess(boolean success) implements EditMessageResult {
    }

    static EditMessageResult of(Message message) {
        return new EditedMessage(message);
    }

    static EditMessageResult inline(boolean success) {
        return new InlineSuccess(success);
    }

    default Message asMessage() {
        return this instanceof EditedMessage em ? em.message() : null;
    }

    default boolean isInline() {
        return this instanceof InlineSuccess;
    }

    class Deserializer extends JsonDeserializer<EditMessageResult> {
        @Override
        public EditMessageResult deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isBoolean()) {
                return new InlineSuccess(node.asBoolean());
            }
            Message msg = ctxt.readTreeAsValue(node, Message.class);
            return new EditedMessage(msg);
        }
    }
}
