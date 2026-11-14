package io.quarkiverse.telegram.bot.test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.quarkiverse.telegram.bot.runtime.model.AnswerCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.CallbackQuery;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.ChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageReplyMarkup;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboardButton;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboardMarkup;
import io.quarkiverse.telegram.bot.runtime.model.InlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResult;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResultArticle;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.Update;
import io.quarkiverse.telegram.bot.runtime.model.User;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Fluent simulator for Telegram bot conversations.
 * Allows simulating user commands, text messages, button clicks, and inline queries,
 * with expressive assertions for bot replies, edited messages, and callback acknowledgments.
 */
public class ConversationSimulator {

    private final TelegramBotTestSupport support;
    private User user;
    private Chat chat;
    private Duration timeout;
    private int expectedStatusCode = 200;
    private String customSecretToken;
    private final Map<String, String> headers = new HashMap<>();

    private static final AtomicLong updateIdCounter = new AtomicLong(1_000_000_000L);
    private final AtomicLong messageIdCounter = new AtomicLong(1);
    private final AtomicLong lastBotMessageId = new AtomicLong(999);

    private int actionRequestIndex = 0;
    private SendMessage lastSentMessage;
    private EditMessageText lastEditedMessage;
    private AnswerCallbackQuery lastAnswerCallbackQuery;
    private AnswerInlineQuery lastAnswerInlineQuery;

    public ConversationSimulator(TelegramBotTestSupport support, User user, Chat chat) {
        this.support = support;
        this.user = user;
        this.chat = chat;
        this.timeout = support.getDefaultTimeout();
        this.actionRequestIndex = getTotalWireMockRequestsCount();
    }

    // --- Configuration / Switching ---

    public ConversationSimulator chat(long chatId) {
        this.chat = Chat.builder()
                .id(chatId)
                .type(chatId == user.id() ? "private" : "group")
                .firstName(chat.firstName())
                .username(chat.username())
                .build();
        return this;
    }

    public ConversationSimulator chat(long chatId, String chatType) {
        this.chat = Chat.builder()
                .id(chatId)
                .type(chatType)
                .firstName(chat.firstName())
                .username(chat.username())
                .build();
        return this;
    }

    public ConversationSimulator chat(Chat chat) {
        this.chat = chat;
        return this;
    }

    public ConversationSimulator asUser(long userId) {
        this.user = User.builder()
                .id(userId)
                .isBot(false)
                .firstName("User" + userId)
                .username("user" + userId)
                .build();
        return this;
    }

    public ConversationSimulator asUser(User user) {
        this.user = user;
        return this;
    }

    public ConversationSimulator withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public ConversationSimulator withSecretToken(String token) {
        this.customSecretToken = token;
        return this;
    }

    public ConversationSimulator withHeader(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public ConversationSimulator expectStatus(int statusCode) {
        this.expectedStatusCode = statusCode;
        return this;
    }

    // --- Actions ---

    /**
     * Simulates the user sending a command (e.g. "/start" or "/help").
     */
    public ConversationSimulator sendsCommand(String command) {
        return sendsMessage(command);
    }

    /**
     * Simulates the user sending a command with arguments (e.g. sendsCommand("/filter", "status", "active")).
     */
    public ConversationSimulator sendsCommand(String command, String... args) {
        String fullCommand = (args == null || args.length == 0)
                ? command
                : command + " " + String.join(" ", args);
        return sendsMessage(fullCommand);
    }

    /**
     * Simulates the user sending a plain text message.
     */
    public ConversationSimulator sendsMessage(String text) {
        long msgId = messageIdCounter.incrementAndGet();
        long updId = updateIdCounter.incrementAndGet();

        Message msg = Message.builder()
                .messageId(msgId)
                .date(Instant.now().getEpochSecond())
                .chat(chat)
                .from(user)
                .text(text)
                .build();

        Update update = Update.builder()
                .updateId(updId)
                .message(msg)
                .build();

        return sendsUpdate(update);
    }

    /**
     * Simulates the user clicking an inline keyboard button with the specified callback data.
     * Automatically links the callback query to the last bot message or chat.
     */
    public ConversationSimulator clicksButton(String callbackData) {
        long targetMsgId = lastBotMessageId.get() > 0 ? lastBotMessageId.get() : 1L;
        return clicksButton(callbackData, targetMsgId);
    }

    /**
     * Simulates the user clicking an inline button on a specific message ID.
     */
    public ConversationSimulator clicksButton(String callbackData, long messageId) {
        long updId = updateIdCounter.incrementAndGet();

        Message msg = Message.builder()
                .messageId(messageId)
                .date(Instant.now().getEpochSecond())
                .chat(chat)
                .from(user)
                .build();

        CallbackQuery cb = CallbackQuery.builder()
                .id("cb_" + updId)
                .from(user)
                .message(msg)
                .data(callbackData)
                .build();

        Update update = Update.builder()
                .updateId(updId)
                .callbackQuery(cb)
                .build();

        return sendsUpdate(update);
    }

    /**
     * Simulates the user sending an inline query (e.g. "@bot search query").
     */
    public ConversationSimulator sendsInlineQuery(String query) {
        return sendsInlineQuery(query, "");
    }

    /**
     * Simulates the user sending an inline query with an offset for pagination.
     */
    public ConversationSimulator sendsInlineQuery(String query, String offset) {
        long updId = updateIdCounter.incrementAndGet();

        InlineQuery iq = InlineQuery.builder()
                .id("iq_" + updId)
                .from(user)
                .query(query)
                .offset(offset)
                .chatType(chat != null ? chat.type() : "private")
                .build();

        Update update = Update.builder()
                .updateId(updId)
                .inlineQuery(iq)
                .build();

        return sendsUpdate(update);
    }

    /**
     * Simulates the user choosing an inline query result.
     */
    public ConversationSimulator sendsChosenInlineResult(String resultId, String query) {
        long updId = updateIdCounter.incrementAndGet();

        ChosenInlineResult chosen = ChosenInlineResult.builder()
                .resultId(resultId)
                .from(user)
                .query(query)
                .inlineMessageId("inline_msg_" + updId)
                .build();

        Update update = Update.builder()
                .updateId(updId)
                .chosenInlineResult(chosen)
                .build();

        return sendsUpdate(update);
    }

    /**
     * Sends a typed {@link Update} to the webhook endpoint.
     */
    public ConversationSimulator sendsUpdate(Update update) {
        try {
            String json = support.getObjectMapper().writeValueAsString(update);
            return sendsRawUpdate(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Update to JSON", e);
        }
    }

    /**
     * Sends raw JSON update to the webhook endpoint.
     */
    public ConversationSimulator sendsRawUpdate(String json) {
        WireMockServer server = support.getWireMockServer();
        this.actionRequestIndex = getTotalWireMockRequestsCount();

        RequestSpecification spec = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(json);

        String token = customSecretToken != null ? customSecretToken : support.getSecretToken();
        if (token != null && !token.isBlank()) {
            spec.header("X-Telegram-Bot-Api-Secret-Token", token);
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            spec.header(entry.getKey(), entry.getValue());
        }

        Response response = spec.when().post(support.getWebhookPath());
        int expected = this.expectedStatusCode;
        this.expectedStatusCode = 200; // Reset expected status back to default for next action

        response.then().statusCode(expected);
        return this;
    }

    // --- Assertions ---

    /**
     * Asserts that the bot sent a message to this chat containing the expected substring.
     */
    public ConversationSimulator assertBotReplied(String expectedSubstring) {
        return assertBotRepliedInternal(expectedSubstring, false);
    }

    /**
     * Asserts that the bot sent a message to this chat with exact text match.
     */
    public ConversationSimulator assertBotRepliedExact(String expectedExactText) {
        return assertBotRepliedInternal(expectedExactText, true);
    }

    /**
     * Asserts that the bot sent a message matching the given regex pattern.
     */
    public ConversationSimulator assertBotRepliedMatching(Pattern pattern) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/sendMessage")) {
                SendMessage msg = parseJson(req.getBodyAsString(), SendMessage.class);
                if (msg != null && isForThisChat(msg.chatId()) && msg.text() != null
                        && pattern.matcher(msg.text()).matches()) {
                    this.lastSentMessage = msg;
                    return true;
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError(String.format(
                    "Expected bot to reply with text matching pattern '%s' for chat %s, but received: %s",
                    pattern.pattern(), chat.id(), getReceivedMessageTextsSinceAction()));
        }
        return this;
    }

    /**
     * Runs custom consumer assertions against the last sent {@link SendMessage}.
     */
    public ConversationSimulator assertBotRepliedWith(Consumer<SendMessage> assertions) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/sendMessage")) {
                SendMessage msg = parseJson(req.getBodyAsString(), SendMessage.class);
                if (msg != null && isForThisChat(msg.chatId())) {
                    this.lastSentMessage = msg;
                    assertions.accept(msg);
                    return true;
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError("No message sent to chat " + chat.id() + " within timeout " + timeout.toMillis() + " ms");
        }
        return this;
    }

    /**
     * Asserts that the last sent message contains an inline keyboard button with the specified label text.
     */
    public ConversationSimulator assertReplyMarkupContains(String buttonText) {
        if (lastSentMessage == null) {
            throw new AssertionError("Cannot assert reply markup: no message was previously captured by assertBotReplied");
        }
        if (!(lastSentMessage.replyMarkup() instanceof InlineKeyboardMarkup inlineKeyboard)) {
            throw new AssertionError("Expected message replyMarkup to be InlineKeyboardMarkup, but found: "
                    + (lastSentMessage.replyMarkup() != null ? lastSentMessage.replyMarkup().getClass().getSimpleName()
                            : "null"));
        }

        for (List<InlineKeyboardButton> row : inlineKeyboard.inlineKeyboard()) {
            for (InlineKeyboardButton btn : row) {
                if (btn.text() != null && btn.text().contains(buttonText)) {
                    return this;
                }
            }
        }

        throw new AssertionError("Button with text containing '" + buttonText + "' not found in inline keyboard: "
                + inlineKeyboard.inlineKeyboard());
    }

    /**
     * Asserts that the last sent message contains an inline button with exact text and callback data.
     */
    public ConversationSimulator assertInlineButton(String buttonText, String callbackData) {
        if (lastSentMessage == null) {
            throw new AssertionError("Cannot assert inline button: no message was previously captured by assertBotReplied");
        }
        if (!(lastSentMessage.replyMarkup() instanceof InlineKeyboardMarkup inlineKeyboard)) {
            throw new AssertionError("Expected message replyMarkup to be InlineKeyboardMarkup, but found: "
                    + (lastSentMessage.replyMarkup() != null ? lastSentMessage.replyMarkup().getClass().getSimpleName()
                            : "null"));
        }

        for (List<InlineKeyboardButton> row : inlineKeyboard.inlineKeyboard()) {
            for (InlineKeyboardButton btn : row) {
                if (buttonText.equals(btn.text()) && callbackData.equals(btn.callbackData())) {
                    return this;
                }
            }
        }

        throw new AssertionError(String.format(
                "Inline button with text '%s' and callbackData '%s' not found in keyboard: %s",
                buttonText, callbackData, inlineKeyboard.inlineKeyboard()));
    }

    /**
     * Asserts that a message edit request (editMessageText or editMessageReplyMarkup) was executed by the bot.
     */
    public ConversationSimulator assertMessageEdited() {
        return assertMessageEdited(null);
    }

    /**
     * Asserts that an editMessageText request containing the expected substring was executed by the bot.
     */
    public ConversationSimulator assertMessageEdited(String expectedSubstring) {
        return assertMessageEditedInternal(expectedSubstring, false);
    }

    /**
     * Asserts that an editMessageText request with exact text match was executed by the bot.
     */
    public ConversationSimulator assertMessageEditedExact(String expectedExactText) {
        return assertMessageEditedInternal(expectedExactText, true);
    }

    /**
     * Runs custom consumer assertions against the last edited {@link EditMessageText}.
     */
    public ConversationSimulator assertMessageEditedWith(Consumer<EditMessageText> assertions) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/editMessageText")) {
                EditMessageText edit = parseJson(req.getBodyAsString(), EditMessageText.class);
                if (edit != null && (edit.chatId() == null || isForThisChat(edit.chatId()))) {
                    this.lastEditedMessage = edit;
                    assertions.accept(edit);
                    return true;
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError("No editMessageText request found for chat " + chat.id() + " within timeout");
        }
        return this;
    }

    /**
     * Asserts that the callback query was acknowledged via answerCallbackQuery.
     */
    public ConversationSimulator assertCallbackAnswered() {
        return assertCallbackAnswered(null);
    }

    /**
     * Asserts that answerCallbackQuery was called with alert or notification text containing expectedText.
     */
    public ConversationSimulator assertCallbackAnswered(String expectedText) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/answerCallbackQuery")) {
                AnswerCallbackQuery answer = parseJson(req.getBodyAsString(), AnswerCallbackQuery.class);
                if (answer != null && (expectedText == null
                        || (answer.text() != null && answer.text().contains(expectedText)))) {
                    this.lastAnswerCallbackQuery = answer;
                    return true;
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError("Expected answerCallbackQuery "
                    + (expectedText != null ? "with text containing '" + expectedText + "' " : "")
                    + "within timeout " + timeout.toMillis() + " ms");
        }
        return this;
    }

    /**
     * Asserts that answerInlineQuery was called.
     */
    public ConversationSimulator assertInlineQueryAnswered() {
        return assertInlineQueryAnswered(null);
    }

    /**
     * Asserts that answerInlineQuery was called with expected number of results.
     */
    public ConversationSimulator assertInlineQueryAnswered(Integer expectedResultCount) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/answerInlineQuery")) {
                AnswerInlineQuery answer = parseAnswerInlineQuery(req.getBodyAsString());
                if (answer != null && (expectedResultCount == null
                        || (answer.results() != null && answer.results().size() == expectedResultCount))) {
                    this.lastAnswerInlineQuery = answer;
                    return true;
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError("Expected answerInlineQuery "
                    + (expectedResultCount != null ? "with " + expectedResultCount + " results " : "")
                    + "within timeout");
        }
        return this;
    }

    /**
     * Asserts that no messages were sent to Telegram API since the last action.
     */
    public ConversationSimulator assertNoMessageSent() {
        sleep(100);
        List<LoggedRequest> requests = getRequestsSinceAction("/bot.*/sendMessage");
        for (LoggedRequest req : requests) {
            SendMessage msg = parseJson(req.getBodyAsString(), SendMessage.class);
            if (msg != null && isForThisChat(msg.chatId())) {
                throw new AssertionError("Expected no sendMessage to chat " + chat.id() + ", but found: " + msg.text());
            }
        }
        return this;
    }

    /**
     * Asserts that no Telegram Bot API requests of any type were made since the last action.
     */
    public ConversationSimulator assertNothingSent() {
        sleep(100);
        WireMockServer server = support.getWireMockServer();
        if (server != null) {
            List<LoggedRequest> all = server.findAll(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*")));
            int newRequests = all.size() - actionRequestIndex;
            if (newRequests > 0) {
                List<String> urls = all.subList(actionRequestIndex, all.size()).stream()
                        .map(LoggedRequest::getUrl)
                        .toList();
                throw new AssertionError("Expected no Telegram Bot API requests, but received " + newRequests + ": " + urls);
            }
        }
        return this;
    }

    /**
     * Low-level WireMock verification helper with retry.
     */
    public ConversationSimulator assertSent(RequestPatternBuilder pattern) {
        WireMockServer server = support.getWireMockServer();
        AssertionError[] lastErr = new AssertionError[1];
        boolean matched = pollUntil(() -> {
            try {
                server.verify(pattern);
                return true;
            } catch (AssertionError e) {
                lastErr[0] = e;
                return false;
            }
        });
        if (!matched && lastErr[0] != null) {
            throw lastErr[0];
        }
        return this;
    }

    // --- Data Accessors ---

    public SendMessage getLastSentMessage() {
        return lastSentMessage;
    }

    public EditMessageText getLastEditedMessage() {
        return lastEditedMessage;
    }

    public AnswerCallbackQuery getLastAnswerCallbackQuery() {
        return lastAnswerCallbackQuery;
    }

    public AnswerInlineQuery getLastAnswerInlineQuery() {
        return lastAnswerInlineQuery;
    }

    public List<SendMessage> getSentMessages() {
        List<SendMessage> list = new ArrayList<>();
        List<LoggedRequest> requests = getRequestsSinceAction("/bot.*/sendMessage");
        for (LoggedRequest req : requests) {
            SendMessage msg = parseJson(req.getBodyAsString(), SendMessage.class);
            if (msg != null && isForThisChat(msg.chatId())) {
                list.add(msg);
            }
        }
        return list;
    }

    public List<EditMessageText> getEditedMessages() {
        List<EditMessageText> list = new ArrayList<>();
        List<LoggedRequest> requests = getRequestsSinceAction("/bot.*/editMessageText");
        for (LoggedRequest req : requests) {
            EditMessageText edit = parseJson(req.getBodyAsString(), EditMessageText.class);
            if (edit != null && (edit.chatId() == null || isForThisChat(edit.chatId()))) {
                list.add(edit);
            }
        }
        return list;
    }

    public void reset() {
        support.reset();
        this.actionRequestIndex = 0;
        this.lastSentMessage = null;
        this.lastEditedMessage = null;
    }

    // --- Internal Helpers ---

    private boolean pollUntil(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            sleep(50);
        }
        return false;
    }

    private ConversationSimulator assertBotRepliedInternal(String expected, boolean exact) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/sendMessage")) {
                SendMessage msg = parseJson(req.getBodyAsString(), SendMessage.class);
                if (msg != null && isForThisChat(msg.chatId()) && msg.text() != null) {
                    boolean matches = exact ? msg.text().equals(expected) : msg.text().contains(expected);
                    if (matches) {
                        this.lastSentMessage = msg;
                        return true;
                    }
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError(String.format(
                    "Expected bot to reply with text %s '%s' for chat %s within %d ms, but received: %s",
                    exact ? "equal to" : "containing", expected, chat.id(), timeout.toMillis(),
                    getReceivedMessageTextsSinceAction()));
        }
        return this;
    }

    private ConversationSimulator assertMessageEditedInternal(String expectedSubstring, boolean exact) {
        boolean matched = pollUntil(() -> {
            for (LoggedRequest req : getRequestsSinceAction("/bot.*/editMessageText")) {
                EditMessageText edit = parseJson(req.getBodyAsString(), EditMessageText.class);
                if (edit != null && (edit.chatId() == null || isForThisChat(edit.chatId()))) {
                    if (expectedSubstring == null) {
                        this.lastEditedMessage = edit;
                        return true;
                    }
                    if (edit.text() != null) {
                        boolean matches = exact ? edit.text().equals(expectedSubstring)
                                : edit.text().contains(expectedSubstring);
                        if (matches) {
                            this.lastEditedMessage = edit;
                            return true;
                        }
                    }
                }
            }

            if (expectedSubstring == null) {
                for (LoggedRequest req : getRequestsSinceAction("/bot.*/editMessageReplyMarkup")) {
                    EditMessageReplyMarkup markup = parseJson(req.getBodyAsString(), EditMessageReplyMarkup.class);
                    if (markup != null && (markup.chatId() == null || isForThisChat(markup.chatId()))) {
                        return true;
                    }
                }
            }
            return false;
        });

        if (!matched) {
            throw new AssertionError(String.format(
                    "Expected message to be edited %sfor chat %s within %d ms, but none was received",
                    expectedSubstring != null ? "with text containing '" + expectedSubstring + "' " : "",
                    chat.id(), timeout.toMillis()));
        }
        return this;
    }

    private boolean isForThisChat(Object targetChatId) {
        if (targetChatId == null) {
            return true;
        }
        return String.valueOf(targetChatId).equals(String.valueOf(chat.id()));
    }

    private List<LoggedRequest> getRequestsSinceAction(String urlPattern) {
        WireMockServer server = support.getWireMockServer();
        if (server == null) {
            return List.of();
        }
        List<LoggedRequest> all = server.findAll(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*")));
        int startIndex = Math.min(actionRequestIndex, all.size());
        List<LoggedRequest> newRequests = all.subList(startIndex, all.size());
        Pattern pattern = Pattern.compile(urlPattern);
        return newRequests.stream()
                .filter(req -> pattern.matcher(req.getUrl()).matches()
                        || req.getUrl().contains(urlPattern.replace("/bot.*/", "")))
                .toList();
    }

    private int getTotalWireMockRequestsCount() {
        WireMockServer server = support.getWireMockServer();
        if (server == null) {
            return 0;
        }
        return server.findAll(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*"))).size();
    }

    private List<String> getReceivedMessageTextsSinceAction() {
        List<String> texts = new ArrayList<>();
        for (LoggedRequest req : getRequestsSinceAction("/bot.*/sendMessage")) {
            SendMessage msg = parseJson(req.getBodyAsString(), SendMessage.class);
            if (msg != null && isForThisChat(msg.chatId())) {
                texts.add(msg.text());
            }
        }
        return texts;
    }

    private <T> T parseJson(String json, Class<T> clazz) {
        try {
            return support.getObjectMapper().readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    private AnswerInlineQuery parseAnswerInlineQuery(String json) {
        try {
            JsonNode root = support.getObjectMapper().readTree(json);
            if (root == null || !root.isObject()) {
                return null;
            }
            String id = root.path("inline_query_id").asText(null);
            JsonNode resultsNode = root.path("results");
            List<InlineQueryResult> resultsList = new ArrayList<>();
            if (resultsNode.isArray()) {
                for (JsonNode res : resultsNode) {
                    InlineQueryResult item = null;
                    try {
                        String type = res.path("type").asText("");
                        if ("article".equals(type)) {
                            item = support.getObjectMapper().treeToValue(res, InlineQueryResultArticle.class);
                        }
                    } catch (Exception ignored) {
                    }
                    resultsList.add(item);
                }
            }
            Integer cacheTime = root.has("cache_time") ? root.path("cache_time").asInt() : null;
            Boolean isPersonal = root.has("is_personal") ? root.path("is_personal").asBoolean() : null;
            String nextOffset = root.has("next_offset") ? root.path("next_offset").asText(null) : null;
            return new AnswerInlineQuery(id, resultsList, cacheTime, isPersonal, nextOffset, null);
        } catch (Exception e) {
            return null;
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
