# Roadmap & Future Enhancements: Quarkus Telegram Bot

Questo documento raccoglie la roadmap delle funzionalità pianificate e i miglioramenti architetturali da implementare nelle prossime sessioni di sviluppo, per portare l'estensione allo standard ufficiale **Quarkiverse** ("batteries-included").

---

## Stato Attuale (Completato)
- ✅ **Milestone 1:** Core runtime, polling runner, webhook handler con secret token, `@TelegramCommand`, CDI routing.
- ✅ **Milestone 2:** Codegen JavaPoet da schema OpenAPI con 232 DTO (record immutabili e sealed interfaces).
- ✅ **Milestone 3:** Supporto file multipart (`InputFile`, `sendPhoto`, `sendDocument`, `sendAudio`, `sendMediaGroup`).
- ✅ **Milestone 4:** FSM conversazionale reattiva (`@OnState`, `@OnStateMismatch`, `ConversationContext`), store di sessione `InMemory` (con eviction timer periodico) e `Redis` (con distributed lock atomico).
- ✅ **Hardening & Bottoni Inline:** Metodi `editMessageText`, `answerCallbackQuery`, sealed interface `EditMessageResult`, memoizzazione code per evitare replay Uni, at-least-once acknowledgment, e risoluzione dei 20 punti di code review.

---

## Prossimi Blocchi di Sviluppo

### 1. Metodi Telegram Client ad Alta Frequenza
Completare la copertura dell'API REST in `TelegramBotApi` e `TelegramBotClient` per le operazioni più comuni nell'interazione utente:

- [ ] **`deleteMessage(long chatId, long messageId)`**:
  - Eliminazione di messaggi di stato temporanei (es. "Elaborazione in corso...").
  - Cancellazione di messaggi con dati sensibili inseriti dall'utente (password, codici OTP).
  - Pulizia dei messaggi di input superflui per mantenere pulita la chat.
- [ ] **`editMessageReplyMarkup(long chatId, long messageId, ReplyMarkup replyMarkup)`**:
  - Aggiornamento dinamico o rimozione dei bottoni inline senza dover ri-inviare o alterare il testo del messaggio.
- [ ] **`sendChatAction(long chatId, String action)`**:
  - Invio dello stato di presenza (`typing`, `upload_photo`, `record_voice`, `upload_document`, `find_location`).
  - Indispensabile per bot con backend lenti, LLM o chiamate a servizi terzi, evitando l'impressione che il bot sia bloccato.
- [ ] **Estensione Supporto Media**:
  - `sendVideo`, `sendAnimation` (supporto nativo per GIF), `sendVoice`.

---

### 2. Gestione Centralizzata delle Eccezioni (`@OnError`)
Introdurre un meccanismo dichiarativo ed ergonomico per catturare errori imprevisti negli handler CDI:

- [x] **Annotazione `@OnError` / `TelegramExceptionHandler`**:
  - Definizione di metodi di gestione errori scoped (nello stesso bean del bot) o globali con priorità alla classe e per specificità dell'eccezione (minima distanza gerarchica).
  - Interfaccia CDI `TelegramExceptionHandler<T extends Throwable>` per registrare handler globali tramite iniezione delle dipendenze.
  - Firme supportate flessibili: `(Throwable error, Update update, ConversationContext ctx, User user, Chat chat, TelegramBotClient client, Message message)`.
  - Invio automatico del messaggio di cortesia o localizzato quando l'handler restituisce `String`, `SendMessage` o `Uni<?>`.
  - Logging strutturato con MDC (`telegram.update_id`, `telegram.chat_id`, `telegram.user_id`) e tracciamento automatico OpenTelemetry span (se attivo).

---

### 3. Sicurezza, Autorizzazione & Interceptors
Aggiungere controlli di accesso granulari e componibili:

- [ ] **Filtro di Autorizzazione Ruoli/Admin**:
  - Annotazione `@BotAdmin` o `@SecuredBot(roles = {"admin"}, allowedUsers = {"12345678"})`.
  - Configurazione da `application.properties`:
    ```properties
    quarkus.telegram.security.admin-ids=123456789,987654321
    ```
- [ ] **Filtro Gruppi e Canali**:
  - Possibilità di limitare gli handler solo a chat private, canali o supergruppi (`@ChatType({PRIVATE, GROUP})`).
- [ ] **Interceptors Lifecycle**:
  - `@BeforeUpdate` / `@AfterUpdate` per logging, metriche Prometheus personalizzate o audit trail.

---

### 4. Quarkus Dev UI v2 (`/q/dev-ui`)
Creare la console interattiva di sviluppo per l'estensione:

- [ ] **Card Telegram Bot**:
  - Identità del bot: Username, nome, supporto modalità (Polling vs Webhook), stato token.
- [ ] **Mappa degli Handler e Grafo FSM**:
  - Tabella di tutti i `@TelegramCommand`, `@OnCallbackQuery` e `@OnMessage` scansionati da Jandex a build-time.
  - Visualizzazione visiva o tabellare degli stati conversazionali `@OnState` e relative transizioni.
- [ ] **Simulatore Interattivo (Dev Console)**:
  - Invio simulato di comandi, messaggi di testo e click su bottoni inline direttamente dal browser senza dover aprire l'app Telegram reale.

---

### 5. Inline Mode & Telegram Mini Apps (Web Apps)
Supporto per casi d'uso avanzati:

- [x] **Inline Query Support**:
  - Annotazione `@OnInlineQuery` con pattern matching e supporto wildcard (es. `@miobot search_*`).
  - Annotazione `@OnChosenInlineResult` per tracciare il click/invio dei risultati da parte dell'utente.
  - Metodo `answerInlineQuery` in `TelegramBotApi` e `TelegramBotClient`.
  - Risoluzione automatica dei parametri (`InlineQuery`, `ChosenInlineResult`, `User`, `String` query).
  - Gestione automatica del valore di ritorno (`List<InlineQueryResult>`, `InlineQueryResult`, `AnswerInlineQuery` o `Uni<?>`).
- [ ] **Validazione Telegram Web App (Mini Apps)**:
  - Utility crittografica per validare a runtime l'hash `initData` generato da Telegram Web Apps (HMAC-SHA256 con bot token) conformemente alle specifiche di sicurezza Telegram.

---

### 6. Testing Harness & DX Avanzata
Semplificare i test per gli sviluppatori di bot:

- [ ] **Modulo `test-framework`**:
  - Fornire un `TelegramBotTestSupport` con mock preconfigurati e un fluent `ConversationSimulator`:
    ```java
    botTester.user(12345L)
        .sendsCommand("/start")
        .assertBotReplied("Benvenuto!")
        .clicksButton("p2_film")
        .assertMessageEdited();
    ```

---

*Creato il 13 Novembre 2026 sul branch `fixes-and-hardening`.*
