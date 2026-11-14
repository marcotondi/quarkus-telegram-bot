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
* **Run a Single Test Class from Root Reactor**:
  ```bash
  mvn test -Dtest=TelegramInlineQueryTest -Dsurefire.failIfNoSpecifiedTests=false
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
  5. `test-framework` (developer testing harness & ConversationSimulator)
  6. `docs` (AsciiDoc documentation generator, enabled by default profile)
  7. `integration-tests` (WireMock & mock-based test suite, enabled by default profile)

* **Cross-Module Dependency Trap**:
  `integration-tests` depends on `runtime`, `deployment`, and `test-framework`. If you modify any of these upstream modules, running `mvn test -pl integration-tests` directly will use stale jars from `~/.m2` or fail with `ClassNotFoundException: HandlerBuildItem`.
  **Fix**: Always run `mvn install -DskipTests -pl runtime,deployment,test-framework` before running a targeted test in `integration-tests`.

* **Model Codegen**:
  The 232 Telegram Bot API 8.x models (records & sealed interfaces) in `runtime/target/generated-sources/telegram` are generated during `runtime` compilation using `exec:java` invoking `TelegramModelGenerator`. If modifying OpenAPI schemas or codegen logic, re-install `codegen` first (`mvn install -pl codegen -DskipTests`).

* **Parent Version & Build Plugins**:
  `parent` is set to `quarkiverse-parent:26` (with `maven-jar-plugin:3.5.1` / `maven-archiver:3.6.6` cached in `~/.m2`). If bumping parent or adding plugins in restricted/offline environments, ensure plugin jars are cached locally or network access is permitted for initial download.

---

## CDI & ArC Gotchas

* **Unremovable Bot Beans**:
  Telegram bot handlers (`@TelegramCommand`, `@OnCallbackQuery`, `@OnMessage`, `@OnStateMismatch`, `@OnInlineQuery`, `@OnChosenInlineResult`, `@OnError`, and beans implementing `TelegramExceptionHandler`) are invoked dynamically by `UpdateDispatcher` and not injected into other beans. ArC will aggressively eliminate them as unused unless protected.
  * *Quirk*: `UnremovableBeanBuildItem.beanClassNames(String...)` passes string sets to `BeanTypesExclusion` which checks against `Set<DotName>`, failing due to Java type erasure.
  * *Pattern*: Use `UnremovableBeanBuildItem(beanInfo -> botClasses.contains(beanInfo.getBeanClass().toString()))` in `TelegramBotProcessor`.

* **Jandex ClassInfo vs Declared Annotations**:
  In Jandex, `ClassInfo.annotation(DotName)` recursively matches annotations found anywhere on the class members (methods, parameters, fields). When checking for class-level annotations (e.g. `@BotAdmin`, `@SecuredBot`, `@ChatType`), always call `ClassInfo.declaredAnnotation(DotName)` to avoid falsely inheriting annotations placed on methods.

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

* **WireMock Harness & Dynamic Port Trap**:
  Integration tests use `TelegramMockServer` (`@QuarkusTestResource(TelegramMockServer.class)`) which stubs Telegram API endpoints (`getMe`, `deleteWebhook`, `sendMessage`, `answerInlineQuery`, etc.) on dynamic ports.
  * *Trap*: **NEVER call static `WireMock.verify(...)`**; it defaults to port `8080` and throws `HttpHostConnectException: Connection refused`. Always call `TelegramMockServer.getWireMockServer().verify(...)` or the test helper `verifyWithRetry(...)`.
* **Redis & DevServices Tests**:
  * Rootless Podman is supported via `/run/user/1000/podman/podman.sock` in `~/.testcontainers.properties` (`docker.host=unix:///run/user/1000/podman/podman.sock`).
  * `TelegramRedisDevServicesTest` uses `RedisTestProfile` (`quarkus.redis.devservices.enabled=true`, `quarkus.redis.devservices.image-name=docker.io/library/redis:7-alpine`, `quarkus.telegram.session.store=redis`) to test live Redis container interactions and FSM conversation persistence against real Redis.
  * Standard tests keep `quarkus.redis.devservices.enabled=false` by default in `integration-tests/src/main/resources/application.properties` to ensure fast offline execution.
  * `RedisSessionStoreTest` provides fast mock-based unit tests for `ReactiveRedisDataSource`.
