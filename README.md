# Quarkus Telegram Bot

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.telegram/quarkus-telegram-bot?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.telegram/quarkus-telegram-bot-parent)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Quarkus extension for Telegram bots: CDI annotations for handlers, Mutiny for async calls, the generated Bot API 8.x models, and webhook or long polling transport.

## Features

- Handlers declared with `@TelegramCommand`, `@OnCallbackQuery`, `@OnMessage`, `@OnInlineQuery`, `@OnState`, `@OnStateMismatch`, `@OnChosenInlineResult`, `@OnError`.
- Multi-step conversations through `ConversationContext` (`transitionTo`, `complete`), stored in memory or Redis.
- Automatic transport: long polling in dev and test, webhook in production, with optional secret token validation.
- 230+ Java 21 records and sealed interfaces covering the Bot API, plus `InlineKeyboard` helpers.
- Streamed uploads of photos, documents, audio and media groups through `InputFile.BinaryPath` and `InputFile.BinaryBytes`.
- WireMock-based `ConversationSimulator` for testing bot flows without Telegram.

## Requirements

- Java 21
- Quarkus 3.40.x

## Installation

```xml
<dependency>
    <groupId>io.quarkiverse.telegram</groupId>
    <artifactId>quarkus-telegram-bot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick start

### 1. Configure the bot

```properties
# Token from @BotFather
quarkus.telegram.token=123456789:ABCdefGHIjklMNOpqrSTUvwxYZ

# Optional: filters commands addressed to this bot in group chats ("/start@MyAwesomeBot")
quarkus.telegram.bot-username=MyAwesomeBot

# AUTO: POLLING in dev/test, WEBHOOK in prod. Can be forced to POLLING or WEBHOOK.
quarkus.telegram.mode=AUTO
```

### 2. Write a handler

```java
package com.example;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.CallbackParam;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnError;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.model.Chat;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.User;

@ApplicationScoped
public class GreetingBot {

    @TelegramCommand(value = "/start", description = "Avvia il bot")
    public SendMessage start(User user, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Benvenuto, " + user.firstName() + "! Vuoi vedere il catalogo?")
                .replyMarkup(InlineKeyboard.of("Catalogo 📚", "catalog_page_1"))
                .build();
    }

    // EditMessageText on a callback fills chatId and messageId from the clicked message.
    @OnCallbackQuery("catalog_page_*")
    public EditMessageText paginate(@CallbackParam String page) {
        int next = Integer.parseInt(page) + 1;
        return EditMessageText.builder()
                .text("Catalogo - Pagina " + page)
                .replyMarkup(InlineKeyboard.of("Pagina successiva ➡️", "catalog_page_" + next))
                .build();
    }

    @OnError(IllegalArgumentException.class)
    public String onValidation(IllegalArgumentException ex) {
        return "Attenzione: " + ex.getMessage();
    }
}
```

Sending is automatic: return a model (`SendMessage`, `EditMessageText`, `DeleteMessage`, `AnswerCallbackQuery`, …), plain `String` for a text reply, or `Uni<T>` for async handlers. `SendMessage` needs an explicit `chatId(chat.id())`; returning `String` does not.

### 3. Run

```bash
mvn quarkus:dev
```

With `AUTO` mode in dev, the bot starts long polling, so no public URL is needed. For webhooks set:

```properties
quarkus.telegram.mode=WEBHOOK
quarkus.telegram.webhook.public-url=https://bot.example.com
quarkus.telegram.webhook.secret-token=<random-secret>
```

### Redis-backed sessions

```properties
quarkus.telegram.session.store=redis
quarkus.redis.hosts=redis://localhost:6379
```

Default store is in-memory; sessions expire after `quarkus.telegram.session.ttl` (default `15m`). The Redis store needs the `quarkus-redis-client` extension. In dev and test mode DevServices can start Redis for you; see [Conversations & FSM](docs/modules/ROOT/pages/conversations.adoc).

## Documentation

- Full guide and FSM examples: [docs/modules/ROOT/pages/index.adoc](docs/modules/ROOT/pages/index.adoc)
- Every configuration key: [docs/modules/ROOT/pages/config-reference.adoc](docs/modules/ROOT/pages/config-reference.adoc)
- Module-by-module walkthrough: [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md)
- Architecture notes: [architecture.md](architecture.md)
- Health check, metrics and build-time validation: [docs/modules/ROOT/pages/observability.adoc](docs/modules/ROOT/pages/observability.adoc)

## Building from source

```bash
# Full reactor build with tests
mvn clean test

# Install snapshots to ~/.m2 without tests
mvn clean install -DskipTests

# Fast compile, skip formatting
mvn compile -Dno-format
```

Modules: `codegen` (model generator), `runtime` (extension runtime), `deployment` (build steps), `test-framework` (development testing harness), `docs` (Antora site), `integration-tests` (WireMock suite).

`integration-tests` consumes the other modules from `~/.m2`. After changing `runtime`, `deployment` or `test-framework`, install them first:

```bash
mvn install -DskipTests -pl runtime,deployment,test-framework
mvn test -pl integration-tests -Dtest=TelegramFsmTest
```

## Testing your bot

`quarkus-telegram-bot-test-framework` mocks the Telegram API and simulates user interactions. Inject `TelegramBotTestSupport` into a `@QuarkusTest`:

```java
botTester.user(12345L)
        .sendsCommand("/start")
        .assertBotReplied("Benvenuto")
        .clicksButton("catalog_page_1")
        .assertMessageEdited();
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
