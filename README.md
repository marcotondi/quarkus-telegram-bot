# Quarkus Telegram Bot

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.telegram/quarkus-telegram-bot?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.telegram/quarkus-telegram-bot-parent)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A high-performance, developer-friendly Quarkus extension for building Telegram bots using declarative annotations, reactive Mutiny APIs, conversational finite state machines (FSM), and automated Webhook or Long Polling transports.

---

## Features

- **Declarative Bot Endpoints**: Route updates with `@TelegramCommand`, `@OnCallbackQuery`, `@OnMessage`, and `@OnStateMismatch`.
- **Conversational Finite State Machine (FSM)**: Maintain persistent multi-step conversations (`ConversationContext`) backed by in-memory storage or Redis.
- **Dual Transports**: Seamless automatic switching between **Long Polling** (dev/test) and **Webhook** (production).
- **Strongly-typed 8.x Models**: Full Telegram Bot API 8.x specification coverage with immutable records and fluent builders.
- **Inline Keyboards & In-Place Pagination**: Built-in support for `EditMessageText` and automated dismissal of callback query loading spinners.
- **Multipart Uploads**: Send photos, documents, audio, and media groups with reactive backpressure and configurable upload timeouts.

---

## Installation

Add the extension dependency to your Quarkus project:

```xml
<dependency>
    <groupId>io.quarkiverse.telegram</groupId>
    <artifactId>quarkus-telegram-bot</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Quick Start

### 1. Configuration

Add your bot credentials to `application.properties`:

```properties
# Bot token from @BotFather
quarkus.telegram.token=123456789:ABCdefGHIjklMNOpqrSTUvwxYZ

# Optional: Bot username for group chat command filtering
quarkus.telegram.bot-username=MyAwesomeBot

# Transport mode: AUTO (default: POLLING in dev/test, WEBHOOK in prod)
quarkus.telegram.mode=AUTO
```

### 2. Create Your Bot

```java
package com.example;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.telegram.bot.runtime.api.CallbackParam;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.model.EditMessageText;
import io.quarkiverse.telegram.bot.runtime.model.InlineKeyboard;
import io.quarkiverse.telegram.bot.runtime.model.Message;
import io.quarkiverse.telegram.bot.runtime.model.SendMessage;
import io.quarkiverse.telegram.bot.runtime.model.User;

@ApplicationScoped
public class GreetingBot {

    @TelegramCommand(value = "/start", description = "Avvia il bot")
    public SendMessage start(User user) {
        return SendMessage.builder()
                .text("Benvenuto, " + user.firstName() + "! Vuoi vedere il catalogo?")
                .replyMarkup(InlineKeyboard.of("Catalogo 📚", "catalog_page_1"))
                .build();
    }

    @OnCallbackQuery("catalog_page_{page}")
    public EditMessageText paginate(@CallbackParam String page) {
        return EditMessageText.builder()
                .text("Catalogo - Pagina " + page)
                .replyMarkup(InlineKeyboard.of("Pagina successiva ➡️", "catalog_page_2"))
                .build();
    }

    @OnInlineQuery("search_*")
    public List<InlineQueryResult> search(InlineQuery query, String queryText) {
        return List.of(
            InlineQueryResultArticle.builder()
                .id("1")
                .title("Cerca: " + queryText)
                .type("article")
                .inputMessageContent(new InputTextMessageContent("Risultato per: " + queryText, null, null, null))
                .build()
        );
    }
}
```

---

## Build & Test

```bash
# Compile and test locally
mvn clean test

# Fast compile skipping checks
mvn compile -Dno-format
```

---

## Documentation

Comprehensive guides, conversation FSM examples, Redis persistence configuration, and reference docs are available in [docs/modules/ROOT/pages/index.adoc](docs/modules/ROOT/pages/index.adoc).
