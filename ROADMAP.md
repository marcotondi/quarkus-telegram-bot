# Roadmap & Future Enhancements: Quarkus Telegram Bot

Questo documento raccoglie la roadmap delle funzionalità implementate e i requisiti necessari per completare e certificare la **versione 1.0.0** dell'estensione secondo lo standard ufficiale **Quarkiverse** ("batteries-included").

---

## Stato Attuale (Completato)

- ✅ **Milestone 1 — Core Runtime & Routing:** Polling runner reattivo, webhook Vert.x HTTP con secret token, `@TelegramCommand`, CDI injection e routing degli eventi.
- ✅ **Milestone 2 — OpenAPI Codegen:** Generatore JavaPoet da schema OpenAPI con 232 DTO immutabili (record con pattern builder e sealed interfaces).
- ✅ **Milestone 3 — Supporto Media Multipart:** Invio file e allegati (`InputFile`, `sendPhoto`, `sendDocument`, `sendAudio`, `sendMediaGroup`, `sendVideo`, `sendAnimation`, `sendVoice`).
- ✅ **Milestone 4 — FSM Conversazionale Reattiva:** Macchina a stati finiti (`@OnState`, `@OnStateMismatch`, `ConversationContext`), store di sessione `InMemory` (con eviction periodico) e `RedisSessionStore` (con distributed lock atomico).
- ✅ **Milestone 5 — Bottoni Inline & Metodi ad Alta Frequenza:** `editMessageText`, `editMessageReplyMarkup`, `answerCallbackQuery`, `deleteMessage`, `sendChatAction`, memoizzazione code per evitare replay Uni, at-least-once acknowledgment.
- ✅ **Milestone 6 — Gestione Centralizzata Eccezioni:** Annotazione dichiarativa `@OnError` e interfaccia globale `TelegramExceptionHandler<T>`, prioritizzazione per specificità dell'eccezione, logging strutturato MDC (`telegram.update_id`, `telegram.chat_id`, `telegram.user_id`).
- ✅ **Milestone 7 — Sicurezza, Filtri Chat & Interceptors:**
  - `@BotAdmin` e `@SecuredBot(roles, allowedUsers)` con integrazione `TelegramRoleResolver`.
  - Filtri contesto `@ChatType({PRIVATE, GROUP, SUPERGROUP, CHANNEL})`.
  - Intercettori ciclo di vita `@BeforeUpdate` e `@AfterUpdate` con priorità ordinata.
- ✅ **Milestone 8 — Inline Mode:**
  - Supporto query inline con `@OnInlineQuery("pattern*")` e tracciamento click con `@OnChosenInlineResult`.
  - Metodo client `answerInlineQuery` con risoluzione automatica parametri e payload.
- ✅ **Milestone 9 — Test Framework & Hardening Test Suite:**
  - Modulo dedicato `test-framework` con simulatore fluent `ConversationSimulator` e server mock `TelegramBotTestResource`.
  - Suite espansa a **107 test** complessivi (22 test unitari in `codegen` e `runtime`, 84 integration test con WireMock e container reale).
  - Test live end-to-end con **DevServices & Redis** reale eseguito con successo tramite Podman rootless (`TelegramRedisDevServicesTest`).

---

## Requisiti per il Rilascio della Versione 1.0.0

Per dichiarare conclusa la **v1.0.0** e pronta per l'onboarding in Quarkiverse, sono pianificati i seguenti blocchi:

### 1. Quarkus Dev UI v2 (`/q/dev-ui`) — *Priorità Alta (P1)*

Fornire la console visuale di diagnostica per gli sviluppatori in modalità `quarkus dev`:

- [ ] **Card Telegram Bot**:
  - Stato identità del bot: Username, Bot ID, modalità attiva (`POLLING` vs `WEBHOOK`), webhook path, stato del token.
- [ ] **Tabella degli Handler & Mappa FSM**:
  - Tabella completa degli handler scansionati da Jandex a build-time: comandi, callback query, messaggi tipizzati, query inline, gestori di errore ed intercettori.
  - Visualizzazione tabellare degli stati della macchina conversazionale (`@OnState`, `@OnStateMismatch`) e delle transizioni associate.

---

### 2. Integrazioni Ecosistema Quarkus (Enterprise Readiness) — *Priorità Media (P2)*

Allineamento con i pattern ufficiali delle estensioni Quarkiverse:

- [ ] **Health Check Opzionale ([SmallRye Health](https://quarkus.io/guides/smallrye-health))**:
  - Fornitura di un `TelegramBotHealthCheck` (readiness) attivo condizionalmente se `quarkus-smallrye-health` è nel classpath.
  - Verifica che il thread di Polling sia operativo e privo di errori critici o che il Webhook sia attivo e configurato.
- [ ] **Metriche Predefinite ([Micrometer](https://quarkus.io/guides/micrometer))**:
  - Esposizione opzionale di metriche out-of-the-box (`telegram.updates.received`, `telegram.updates.duration`, `telegram.updates.errors`).
- [ ] **Validazione a Build-Time (Fail-Fast)**:
  - Diagnostica statica in `TelegramBotProcessor` per intercettare a tempo di compilazione firme di metodi annotati non conformi o conflitti su comandi duplicati non filtrati da `@ChatType`.

---

### 3. Certificazione Finale & Documentazione — *Priorità Pre-Release (P3)*

- [ ] **Verifica Eseguibile Nativo (GraalVM / Mandrel)**:
  - Esecuzione del test nativo `TelegramBotResourceIT` (`mvn verify -Pnative`) in container Podman per verificare l'assenza di gap di reflection a runtime.
- [X] **Allineamento Documentazione**:
  - Completamento di `config-reference.adoc` con la lista esaustiva di tutte le property di configurazione (`quarkus.telegram.*`).
  - Documentazione delle guide su Redis DevServices, Interceptors e filtri di sicurezza.

---

## Backlog Post-1.0.0 (Versioni Future)

Funzionalità avanzate pianificate per rilasci successivi:

- [ ] **Validazione Telegram Mini Apps (Web Apps)**:
  - Utility crittografica per validare a runtime l'hash `initData` generato dalle Web Apps di Telegram (HMAC-SHA256 con bot token).
- [ ] **Simulatore Interattivo Web in Dev UI**:
  - Console interattiva browser per inviare comandi, messaggi ed eventi bottoni fittizi direttamente da `/q/dev-ui`.
- [x] **Rate Limiting Dinamico**:
  - Filtro o interceptor per throttling automatico per utente/chat (ad es. per proteggere il bot da spam).

---

*Ultimo aggiornamento: 14 Novembre 2026 sul branch `fixes-and-hardening`.*
