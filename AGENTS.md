# AGENTS.md - Developer Guide for Quarkus Telegram Bot

## Essential Constraints
* **NEVER PUSH TO REMOTE**: All commits and branches must remain strictly local unless the user explicitly requests a push.
* **Java 21 Required**: Targeted for Java 21 (`<maven.compiler.release>21</maven.compiler.release>`) and Quarkus 3.40.x.

---

## Build & Test Commands

* **Full Build & Test (Reactor)**:
  ```bash
  mvn clean test
  ```
* **Install Local Snapshots to `~/.m2` (skipping tests)**:
  ```bash
  mvn clean install -DskipTests
  ```
* **Run a Single Test Class**:
  ```bash
  mvn test -pl integration-tests -Dtest=TelegramFsmTest
  ```
* **Run a Single Test Method**:
  ```bash
  mvn test -pl integration-tests -Dtest=TelegramFsmTest#testFullBookingConversationFlow
  ```
* **Skip Formatting Checks / Fast Compile**:
  ```bash
  mvn compile -Dno-format
  ```

---

## Architecture & Multi-Module Quirks

* **Reactor Order**:
  1. `parent` (`pom.xml`)
  2. `codegen` (`TelegramModelGenerator` JavaPoet generator)
  3. `runtime` (core extension runtime, models, transport, dispatcher, session)
  4. `deployment` (Quarkus build steps & bytecode augmentor)
  5. `docs` (AsciiDoc documentation generator, enabled by default profile)
  6. `integration-tests` (WireMock & mock-based test suite, enabled by default profile)

* **Cross-Module Dependency Trap**:
  `integration-tests` depends on both `runtime` and `deployment`. If you modify `runtime` or `deployment`, running `mvn test -pl integration-tests` directly will use stale jars from `~/.m2` or fail with `ClassNotFoundException: HandlerBuildItem`.
  **Fix**: Always run `mvn install -DskipTests -pl runtime,deployment` before running a targeted test in `integration-tests`.

* **Model Codegen**:
  The 232 Telegram Bot API 8.x models (records & sealed interfaces) in `runtime/target/generated-sources/telegram` are generated during `runtime` compilation using `exec:java` invoking `TelegramModelGenerator`. If modifying OpenAPI schemas or codegen logic, re-install `codegen` first (`mvn install -pl codegen -DskipTests`).

---

## CDI & ArC Gotchas

* **Unremovable Bot Beans**:
  Telegram bot handlers (`@TelegramCommand`, `@OnCallbackQuery`, `@OnMessage`, `@OnStateMismatch`) are invoked dynamically by `UpdateDispatcher` and not injected into other beans. ArC will aggressively eliminate them as unused unless protected.
  * *Quirk*: `UnremovableBeanBuildItem.beanClassNames(String...)` passes string sets to `BeanTypesExclusion` which checks against `Set<DotName>`, failing due to Java type erasure.
  * *Pattern*: Use `UnremovableBeanBuildItem(beanInfo -> botClasses.contains(beanInfo.getBeanClass().toString()))` in `TelegramBotProcessor`.

* **Dynamic Classloading in Conversation Context**:
  When deserializing session payloads in `ConversationContextImpl`, do not use 1-arg `Class.forName()`. Use `Thread.currentThread().getContextClassLoader().loadClass()` so application-level record payloads are visible from the extension runtime jar.

---

## Mutiny & Concurrency Quirks

* **SessionKey Serialization**:
  `UpdateDispatcher` serializes updates per `SessionKey(chatId, userId)` using a concurrent map lock (`sessionLocks.compute()`) to avoid race conditions on rapid taps.
* **Mutiny Uni Recovery & Chaining**:
  In Mutiny, `.onItemOrFailure().transformToUni()` or `.onFailure().recoverWithNull().chain(() -> ...)` must be used for chained sequence operations (`recoverWithUni` does not exist on `UniOnItemOrFailure`).

---

## Testing & DevServices

* **WireMock Harness**:
  Integration tests use `TelegramMockServer` (`@QuarkusTestResource(TelegramMockServer.class)`) which stubs Telegram API endpoints (`getMe`, `deleteWebhook`, `sendMessage`) on dynamic ports.
* **Redis Tests**:
  `RedisSessionStoreTest` uses Mockito mocks on `ReactiveRedisDataSource` rather than live Testcontainers, preventing Docker/Podman socket permission issues. `quarkus.redis.devservices.enabled=false` is set in `integration-tests/src/main/resources/application.properties`.
