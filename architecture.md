# Architecture Design Document: Quarkus Native Telegram Bot SDK

**Coordinate target:** `io.quarkiverse.telegram:quarkus-telegram-bot`  
**Stack tecnologico:** Java 21+, Quarkus (ArC, Vert.x, Mutiny, REST Client Reactive Jackson), GraalVM / Mandrel Native, JavaPoet.

---

## 1. Visione e Idea di Fondo

### Il problema nell'ecosistema attuale
Attualmente, lo sviluppo di bot Telegram in ecosistema Quarkus presenta un divario architetturale netto:
1. **`camel-quarkus-telegram`:** È l'unica integrazione ufficiale ben supportata in modalità native, ma impone il modello mentale, le astrazioni e il peso delle dipendenze di Apache Camel anche per bot semplici.
2. **Librerie Java tradizionali (`rubenlagus/TelegramBots`, `java-telegram-bot-api`):** Sono progettate per la JVM classica. Utilizzano client HTTP esterni (OkHttp, Apache HttpClient), fanno uso estensivo di reflection per serializzare/deserializzare i DTO e ignorano sia il container CDI di Quarkus (**ArC**) sia il motore non-bloccante **Vert.x**. La compilazione in native richiede configurazioni manuali (`reflect-config.json`, certificati SSL, proxy) e vanifica gran parte dei vantaggi di Quarkus.

### Obiettivi progettuali
Creare un'estensione Quarkus nativa costruita sul paradigma **Build-Time First**:
* **Zero Reflection & Instant Startup:** Scansione delle classi e dei metadati a *build-time* tramite **Jandex**, generazione di invoker o registrazione mirata per GraalVM, e uso esclusivo del **Quarkus REST Client Reactive**. Target: binari nativi da ~35–50 MB, consumo minimo di RAM e startup nell'ordine delle decine di millisecondi.
* **Serverless & Scale-to-Zero Ready:** Ottimizzato per l'esecuzione basata su **Webhook** su AWS Lambda, Google Cloud Run o Knative, azzerando l'impatto del cold-start.
* **Developer Experience (DX) idiomatica CDI:** Routing dichiarativo basato su annotazioni CDI (`@TelegramCommand`, `@OnCallbackQuery`, `@OnMessage`, `@OnState`), iniezione dinamica dei parametri e pipeline reattive Mutiny (`Uni<T>`).
* **Zero-Config Dev/Prod Switch:** Attivazione automatica del **Long Polling** in `quarkus:dev` (eliminando la necessità di tunnel esterni come ngrok in locale) e passaggio trasparente al **Webhook** in build di produzione.
* **Manutenibilità automatizzata:** Generazione automatica dei DTO come **Java 21 Record** e **Sealed Interface** a partire dallo schema OpenAPI delle Telegram Bot API.

---

## 2. Architettura Multi-Modulo

Il progetto segue la struttura standard delle estensioni **Quarkiverse**, separando nettamente il codice eseguito a *build-time* da quello incluso nell'artefatto finale (`runtime`), con l'aggiunta di un modulo interno `codegen` per la generazione dei modelli:

```text
quarkus-telegram-bot/
├── pom.xml                                  # Parent POM (allineato a quarkus-bom / quarkiverse-parent)
├── codegen/                                 # Generatore JavaPoet (eseguito solo a build-time, non pubblicato)
│   ├── pom.xml
│   └── src/main/java/io/quarkiverse/telegram/bot/codegen/
│       └── TelegramModelGenerator.java
├── runtime/                                 # Classpath finale dell'applicazione
│   ├── src/main/resources/schema/
│   │   └── telegram-bot-api.json            # Snapshot versionato delle Bot API
│   ├── target/generated-sources/telegram/   # Record e Sealed Interface generati (esclusi da git)
│   └── src/main/java/io/quarkiverse/telegram/bot/runtime/
│       ├── api/                             # Annotazioni pubbliche (@TelegramCommand, @OnState, ecc.)
│       ├── model/                           # InputFile e tipi custom non generati
│       ├── client/                          # TelegramBotApi (REST Client) e TelegramBotClient (Facade)
│       ├── config/                          # Interfacce @ConfigMapping
│       ├── transport/                       # PollingRunner (Mutiny) e WebhookHandler (Vert.x)
│       ├── session/                         # ConversationContext, SessionStore (InMemory / Redis)
│       ├── dispatcher/                      # UpdateDispatcher e risoluzione parametri
│       └── TelegramBotRecorder.java         # Ponte tra build-time (@Record) e runtime
├── deployment/                              # Usato SOLO durante la compilazione JVM / Native
│   └── src/main/java/io/quarkiverse/telegram/bot/deployment/
│       ├── TelegramBotProcessor.java        # @BuildStep: Jandex scan, validazione fail-fast, GraalVM config
│       └── HandlerBuildItem.java            # Metadati interni scambiati tra BuildStep
└── integration-tests/                       # Suite di test JVM e Native (-Dnative) con WireMock e DevServices
```

---

## 3. Configurazione (`@ConfigMapping`)

La configurazione è esposta nel modulo `runtime` sotto il prefisso `quarkus.telegram`. La modalità `AUTO` seleziona il trasporto in base al `LaunchMode` di Quarkus (`DEVELOPMENT`/`TEST` $\rightarrow$ `POLLING`, `NORMAL` $\rightarrow$ `WEBHOOK`).

```java
package io.quarkiverse.telegram.bot.runtime.config;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "quarkus.telegram")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface TelegramBotConfig {

    /** Token rilasciato da @BotFather */
    String token();

    /** AUTO = POLLING in dev/test, WEBHOOK in prod */
    @WithDefault("AUTO")
    TransportMode mode();

    ClientConfig client();
    PollingConfig polling();
    WebhookConfig webhook();
    SessionConfig session();

    enum TransportMode { AUTO, POLLING, WEBHOOK }

    interface ClientConfig {
        @WithDefault("[https://api.telegram.org](https://api.telegram.org)")
        String baseUrl();

        /** Timeout esteso per upload multipart di file fino a 50 MB */
        @WithDefault("60s")
        Duration uploadTimeout();
    }

    interface PollingConfig {
        @WithDefault("30s")
        Duration timeout();

        @WithDefault("100")
        int limit();
    }

    interface WebhookConfig {
        /** URL pubblico HTTPS (es. [https://bot.example.com](https://bot.example.com)) */
        Optional<String> publicUrl();

        @WithDefault("/telegram/webhook")
        String path();

        /** Header X-Telegram-Bot-Api-Secret-Token per validare la provenienza delle richieste */
        Optional<String> secretToken();

        @WithDefault("true")
        boolean autoRegister();
    }

    interface SessionConfig {
        /** Backend di persistenza degli stati: memory | redis */
        @WithDefault("memory")
        String store();

        /** Scadenza automatica delle sessioni inattive */
        @WithDefault("15m")
        Duration ttl();
    }
}
```

---

## 4. Generazione Automatica del Modello (`codegen` + JavaPoet)

### Strategia offline e riproducibilità
Per evitare chiamate di rete durante `mvn compile` (che romperebbero le build riproducibili e offline), lo schema JSON delle Bot API (mantenuto dalla community su `ark0f/telegram-bot-api-openapi` o `PaulSonOfLars/telegram-bot-api-spec`) viene salvato nel repository in `runtime/src/main/resources/schema/telegram-bot-api.json`. L'aggiornamento avviene tramite un profilo Maven dedicato (`-Pupdate-schema`) o una GitHub Action periodica.

### Perché JavaPoet invece di `openapi-generator`
* Genera **Java 21 `record`** immutabili anziché POJO verbosi con getter/setter.
* Imposta `@JsonIgnoreProperties(ignoreUnknown = true)` di default, rendendo il bot immune all'aggiunta di nuovi campi lato Telegram.
* Mappa i tipi polimorfici (`oneOf`) direttamente in **`sealed interface` + `permits`**, abilitando il *Pattern Matching* esaustivo di Java 21.
* Sanitizza le keyword riservate di Java (es. `default` $\rightarrow$ `defaultValue` mantenendo `@JsonProperty("default")`).

### Configurazione Maven (`runtime/pom.xml`)

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>generate-telegram-records</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>java</goal>
                    </goals>
                    <configuration>
                        <mainClass>io.quarkiverse.telegram.bot.codegen.TelegramModelGenerator</mainClass>
                        <arguments>
                            <argument>${project.basedir}/src/main/resources/schema/telegram-bot-api.json</argument>
                            <argument>${project.build.directory}/generated-sources/telegram</argument>
                            <argument>io.quarkiverse.telegram.bot.runtime.model</argument>
                        </arguments>
                        <includeProjectDependencies>false</includeProjectDependencies>
                        <includePluginDependencies>true</includePluginDependencies>
                    </configuration>
                </execution>
            </executions>
            <dependencies>
                <dependency>
                    <groupId>io.quarkiverse.telegram</groupId>
                    <artifactId>quarkus-telegram-bot-codegen</artifactId>
                    <version>${project.version}</version>
                </dependency>
            </dependencies>
        </plugin>

        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>build-helper-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>add-generated-sources</id>
                    <phase>generate-sources</phase>
                    <goals>
                        <goal>add-source</goal>
                    </goals>
                    <configuration>
                        <sources>
                            <source>${project.build.directory}/generated-sources/telegram</source>
                        </sources>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Implementazione di `TelegramModelGenerator`

```java
package io.quarkiverse.telegram.bot.codegen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.javapoet.*;

import javax.lang.model.element.Modifier;
import java.nio.file.Path;
import java.util.*;

public class TelegramModelGenerator {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "default", "class", "public", "private", "new", "return", "switch", "case"
    );

    public static void main(String[] args) throws Exception {
        Path schemaPath = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);
        String targetPackage = args[2];

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(schemaPath.toFile());
        JsonNode schemas = root.path("components").path("schemas");

        Map<String, List<String>> sealedPermits = collectSealedPermits(schemas);

        var fields = schemas.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String schemaName = entry.getKey();
            JsonNode schema = entry.getValue();

            TypeSpec typeSpec = schema.has("oneOf")
                ? buildSealedInterface(schemaName, schema, targetPackage)
                : buildRecord(schemaName, schema, sealedPermits.getOrDefault(schemaName, List.of()), targetPackage);

            JavaFile.builder(targetPackage, typeSpec)
                .skipJavaLangImports(true)
                .build()
                .writeTo(outputDir);
        }
    }

    private static TypeSpec buildRecord(
            String name,
            JsonNode schema,
            List<String> superInterfaces,
            String pkg) {

        Set<String> requiredFields = new HashSet<>();
        schema.path("required").forEach(n -> requiredFields.add(n.asText()));

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder();
        JsonNode props = schema.path("properties");

        var propFields = props.fields();
        while (propFields.hasNext()) {
            var prop = propFields.next();
            String jsonName = prop.getKey();
            JsonNode propSchema = prop.getValue();
            boolean isRequired = requiredFields.contains(jsonName);

            TypeName fieldType = resolveType(propSchema, isRequired, pkg);
            String javaFieldName = toCamelCaseSafe(jsonName);

            ParameterSpec param = ParameterSpec.builder(fieldType, javaFieldName)
                .addAnnotation(AnnotationSpec.builder(JsonProperty.class)
                    .addMember("value", "$S", jsonName)
                    .build())
                .build();

            constructor.addParameter(param);
        }

        TypeSpec.Builder recordBuilder = TypeSpec.recordBuilder(name)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(JsonIgnoreProperties.class)
                .addMember("ignoreUnknown", "$L", true)
                .build())
            .addAnnotation(AnnotationSpec.builder(JsonInclude.class)
                .addMember("value", "$T.NON_NULL", JsonInclude.Include.class)
                .build())
            .recordConstructor(constructor.build());

        for (String iface : superInterfaces) {
            recordBuilder.addSuperinterface(ClassName.get(pkg, iface));
        }

        return recordBuilder.build();
    }

    private static TypeSpec buildSealedInterface(String name, JsonNode schema, String pkg) {
        TypeSpec.Builder ifaceBuilder = TypeSpec.interfaceBuilder(name)
            .addModifiers(Modifier.PUBLIC, Modifier.SEALED);

        for (JsonNode sub : schema.path("oneOf")) {
            String ref = sub.path("$ref").asText();
            String subName = ref.substring(ref.lastIndexOf('/') + 1);
            ifaceBuilder.addPermittedSubclass(ClassName.get(pkg, subName));
        }
        return ifaceBuilder.build();
    }

    private static TypeName resolveType(JsonNode prop, boolean required, String pkg) {
        if (prop.has("$ref")) {
            String ref = prop.get("$ref").asText();
            return ClassName.get(pkg, ref.substring(ref.lastIndexOf('/') + 1));
        }
        return switch (prop.path("type").asText()) {
            case "integer" -> "int64".equals(prop.path("format").asText())
                ? (required ? TypeName.LONG : TypeName.LONG.box())
                : (required ? TypeName.INT : TypeName.INT.box());
            case "number" -> required ? TypeName.DOUBLE : TypeName.DOUBLE.box();
            case "boolean" -> required ? TypeName.BOOLEAN : TypeName.BOOLEAN.box();
            case "string" -> ClassName.get(String.class);
            case "array" -> ParameterizedTypeName.get(
                ClassName.get(List.class),
                resolveType(prop.path("items"), false, pkg)
            );
            default -> ClassName.get(Object.class);
        };
    }

    private static Map<String, List<String>> collectSealedPermits(JsonNode schemas) {
        Map<String, List<String>> childToParents = new HashMap<>();
        var it = schemas.fields();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().has("oneOf")) {
                String parent = entry.getKey();
                for (JsonNode sub : entry.getValue().path("oneOf")) {
                    String ref = sub.path("$ref").asText();
                    String child = ref.substring(ref.lastIndexOf('/') + 1);
                    childToParents.computeIfAbsent(child, k -> new ArrayList<>()).add(parent);
                }
            }
        }
        return childToParents;
    }

    private static String toCamelCaseSafe(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean upperNext = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else {
                sb.append(upperNext ? Character.toUpperCase(c) : c);
                upperNext = false;
            }
        }
        String candidate = sb.toString();
        return JAVA_KEYWORDS.contains(candidate) ? candidate + "Value" : candidate;
    }
}
```

### Esempi di codice generato

```java
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Message(
    @JsonProperty("message_id") long messageId,
    @JsonProperty("date") long date,
    @JsonProperty("chat") Chat chat,
    @JsonProperty("from") User from,
    @JsonProperty("text") String text,
    @JsonProperty("photo") List<PhotoSize> photo
) {}

public sealed interface BotCommandScope permits
    BotCommandScopeDefault,
    BotCommandScopeAllPrivateChats,
    BotCommandScopeAllGroupChats,
    BotCommandScopeChat {}
```

*(Nota: per le `sealed interface` deserializzate in ingresso da Telegram, come `ChatMember` o `MaybeInaccessibleMessage`, il generatore aggiunge `@JsonTypeInfo` e `@JsonSubTypes` leggendo il campo `discriminator` dallo schema OpenAPI).*

---

## 5. Developer Experience e Routing CDI (`runtime.api`)

Lo sviluppatore implementa la logica del bot tramite normali bean CDI (`@ApplicationScoped`), senza ereditare classi base né fare parsing manuale dell'oggetto `Update`.

### Annotazioni principali
* `@TelegramCommand(value = "/start", description = "...", interruptsState = false)`: intercetta i comandi. Le descrizioni vengono registrate automaticamente su Telegram a startup tramite `setMyCommands`.
* `@OnCallbackQuery("order_*")`: intercetta i click su bottoni inline con supporto wildcard/regex ed estrazione parametri via `@CallbackParam`.
* `@OnMessage(type = MessageType.TEXT)`: intercetta messaggi filtrati per tipologia (`TEXT`, `PHOTO`, `DOCUMENT`, `VOICE`, `LOCATION`, `ANY`).
* `@OnState("STATE_NAME")`: vincola l'handler a uno stato attivo della macchina a stati conversazionale.

### Risoluzione dinamica dei parametri e tipi di ritorno
L'`UpdateDispatcher` inietta i parametri in base al tipo dichiarato nella firma del metodo (`User`, `Chat`, `Message`, `CallbackQuery`, `Update`, `TelegramBotClient`, `ConversationContext`) e converte automaticamente il valore di ritorno:
* `String` $\rightarrow$ invia automaticamente un `sendMessage` alla chat corrente.
* `SendMessage` / DTO outbound $\rightarrow$ esegue la chiamata API corrispondente.
* `Uni<T>` $\rightarrow$ mantiene l'intera esecuzione non-bloccante sull'event-loop Vert.x.

```java
@ApplicationScoped
public class OrdiniBot {

    @Inject
    OrdiniService ordiniService;

    @TelegramCommand(value = "/start", description = "Menu principale")
    public SendMessage onStart(User user, Chat chat) {
        return SendMessage.builder()
                .chatId(chat.id())
                .text("Ciao " + user.firstName() + ", cosa vuoi fare?")
                .replyMarkup(InlineKeyboard.of("Nuovo ordine", "order_new"))
                .build();
    }

    @OnCallbackQuery("order_*")
    public Uni<String> onOrderAction(CallbackQuery query, @CallbackParam String action) {
        return ordiniService.elabora(query.from().id(), action)
                .map(esito -> "Operazione completata: " + esito);
    }

    @OnMessage(type = MessageType.PHOTO)
    public Uni<Void> onRicevuta(Message message, TelegramBotClient client) {
        String fileId = message.photo().getLast().fileId();
        return client.sendMessage(message.chat().id(), "Foto ricevuta: " + fileId)
                     .replaceWithVoid();
    }
}
```

---

## 6. Trasporto HTTP e Upload File Multipart in GraalVM Native

### Modellazione di `InputFile` (`sealed interface`)
In Telegram un file può essere inviato tramite `file_id`, `URL` oppure upload binario `multipart/form-data`. La modellazione tramite `sealed interface` permette di instradare la richiesta sul canale più efficiente tramite *Pattern Matching*:

```java
package io.quarkiverse.telegram.bot.runtime.model;

import java.nio.file.Path;

public sealed interface InputFile permits InputFile.FileId, InputFile.Url, InputFile.BinaryPath, InputFile.BinaryBytes {

    record FileId(String value) implements InputFile {}
    record Url(String value) implements InputFile {}
    record BinaryPath(Path path, String fileName, String contentType) implements InputFile {}
    record BinaryBytes(byte[] data, String fileName, String contentType) implements InputFile {}

    static InputFile fromFileId(String fileId) { return new FileId(fileId); }
    static InputFile fromUrl(String url) { return new Url(url); }
    static InputFile fromPath(Path path) {
        return new BinaryPath(path, path.getFileName().toString(), "application/octet-stream");
    }
    static InputFile fromBytes(byte[] data, String fileName, String contentType) {
        return new BinaryBytes(data, fileName, contentType);
    }
}
```

### Doppio canale REST Client: JSON vs `MultipartForm` Vert.x
Per evitare la reflection richiesta dalle classi `@BeanParam` con `@RestForm` in GraalVM, l'upload binario utilizza direttamente **`io.vertx.ext.web.multipart.MultipartForm`**:
1. **Zero reflection** sul payload multipart.
2. **Presenza garantita dell'attributo `filename`** nell'header `Content-Disposition` (obbligatorio per Telegram anche per array di byte in memoria).
3. **Streaming asincrono da disco:** `binaryFileUpload(..., path.toString(), ...)` legge file fino a 50 MB a chunk asincroni senza caricarli interamente nella heap del binario nativo.
4. **Supporto a `attach://`:** consente nomi di campi dinamici per `sendMediaGroup`.

```java
@Path("/bot{token}")
public interface TelegramBotApi {

    @POST
    @Path("/getUpdates")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<List<Update>>> getUpdates(@PathParam("token") String token, GetUpdatesRequest req);

    @POST
    @Path("/setWebhook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Boolean>> setWebhook(@PathParam("token") String token, SetWebhookRequest req);

    @POST
    @Path("/sendMessage")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendMessage(@PathParam("token") String token, SendMessage req);

    // Canale 1: JSON puro per FileId / Url
    @POST
    @Path("/sendPhoto")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendPhotoJson(@PathParam("token") String token, SendPhotoJsonRequest request);

    // Canale 2: Multipart streaming per file binari
    @POST
    @Path("/sendPhoto")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<TelegramResponse<Message>> sendPhotoMultipart(@PathParam("token") String token, MultipartForm form);
}
```

### Facade `TelegramBotClient` (con gestione `reply_markup` in multipart)
Nelle richieste `multipart/form-data`, Telegram richiede che eventuali oggetti annidati come `reply_markup` vengano serializzati esplicitamente come **stringa JSON** all'interno di un attributo testuale del form.

```java
@ApplicationScoped
public class TelegramBotClient {

    private final TelegramBotApi api;
    private final TelegramBotConfig config;
    private final ObjectMapper objectMapper;

    @Inject
    public TelegramBotClient(@RestClient TelegramBotApi api,
                             TelegramBotConfig config,
                             ObjectMapper objectMapper) {
        this.api = api;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public Uni<Message> sendPhoto(long chatId, InputFile photo, String caption, ReplyMarkup markup) {
        return switch (photo) {
            case InputFile.FileId(var id) -> api.sendPhotoJson(
                    config.token(), new SendPhotoJsonRequest(chatId, id, caption, markup)
            ).map(TelegramResponse::result);

            case InputFile.Url(var url) -> api.sendPhotoJson(
                    config.token(), new SendPhotoJsonRequest(chatId, url, caption, markup)
            ).map(TelegramResponse::result);

            case InputFile.BinaryPath(var path, var fileName, var contentType) -> {
                MultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("photo", fileName, path.toAbsolutePath().toString(), contentType);
                yield api.sendPhotoMultipart(config.token(), form)
                         .map(TelegramResponse::result);
            }

            case InputFile.BinaryBytes(var bytes, var fileName, var contentType) -> {
                MultipartForm form = baseMultipart(chatId, caption, markup)
                        .binaryFileUpload("photo", fileName, Buffer.buffer(bytes), contentType);
                yield api.sendPhotoMultipart(config.token(), form)
                         .map(TelegramResponse::result);
            }
        };
    }

    private MultipartForm baseMultipart(long chatId, String caption, ReplyMarkup markup) {
        MultipartForm form = MultipartForm.create()
                .attribute("chat_id", String.valueOf(chatId));

        if (caption != null) {
            form.attribute("caption", caption);
        }
        if (markup != null) {
            try {
                form.attribute("reply_markup", objectMapper.writeValueAsString(markup));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Impossibile serializzare reply_markup", e);
            }
        }
        return form;
    }
}
```

### Meccanismi di ricezione (Polling vs Webhook)
* **Long Polling Engine:** Loop reattivo non-bloccante costruito con `Multi.createBy().repeating()` che invoca `getUpdates` sullo stesso event-loop Vert.x mantenendo l'offset `lastUpdateId + 1`.
* **Webhook Handler:** Handler HTTP Vert.x montato su `quarkus.telegram.webhook.path`. Valida l'header `X-Telegram-Bot-Api-Secret-Token`, deserializza `Update`, delega all'`UpdateDispatcher` e risponde immediatamente `200 OK`.

---

## 7. Macchina a Stati Finiti (FSM) e Sessioni Conversazionali

### Chiave composita e `ConversationContext`
Per supportare correttamente sia le chat private che i gruppi (dove più utenti interagiscono contemporaneamente nella stessa chat), lo stato è indicizzato sulla coppia `(chatId, userId)`:

```java
package io.quarkiverse.telegram.bot.runtime.session;

public record SessionKey(long chatId, long userId) {
    public String toStorageKey() {
        return "tg:session:" + chatId + ":" + userId;
    }
}

public interface ConversationContext {
    Optional<String> currentState();
    <T> Optional<T> data(Class<T> type);
    void transitionTo(String nextState, Object data);
    void transitionTo(String nextState);
    void complete();
}
```

### Esempio di flusso conversazionale multi-step

```java
@ApplicationScoped
public class PrenotazioneFlow {

    // Registrato automaticamente per reflection su GraalVM dal processor
    @SessionPayload
    public record BozzaPrenotazione(String data, Integer ospiti) {}

    @TelegramCommand(value = "/prenota", description = "Avvia una nuova prenotazione")
    public String avvia(ConversationContext ctx) {
        ctx.transitionTo("ATTESA_DATA", new BozzaPrenotazione(null, null));
        return "Per quale giorno vuoi prenotare? (es. 25/11)";
    }

    @OnState("ATTESA_DATA")
    @OnMessage(type = MessageType.TEXT)
    public SendMessage riceviData(Message msg, ConversationContext ctx) {
        String dataScelta = msg.text().trim();
        ctx.transitionTo("ATTESA_OSPITI", new BozzaPrenotazione(dataScelta, null));

        return SendMessage.builder()
                .chatId(msg.chat().id())
                .text("Perfetto, data: " + dataScelta + ". Quanti ospiti sarete?")
                .replyMarkup(InlineKeyboard.of("2 persone", "ospiti_2", "4 persone", "ospiti_4"))
                .build();
    }

    @OnState("ATTESA_OSPITI")
    @OnCallbackQuery("ospiti_*")
    public String conferma(CallbackQuery query, @CallbackParam String numero, ConversationContext ctx) {
        var bozza = ctx.data(BozzaPrenotazione.class).orElseThrow();
        int ospiti = Integer.parseInt(numero);

        ctx.complete();
        return "Prenotazione confermata per " + ospiti + " persone il " + bozza.data() + "!";
    }

    @TelegramCommand(value = "/annulla", interruptsState = true)
    public String annulla(ConversationContext ctx) {
        if (ctx.currentState().isEmpty()) {
            return "Nessuna operazione in corso.";
        }
        ctx.complete();
        return "Operazione annullata.";
    }
}
```

### Priorità di routing e protezione dalle Race Condition
1. **Comandi con `interruptsState = true`:** hanno precedenza assoluta per evitare che un utente resti bloccato in uno stato.
2. **Handler `@OnState` attivi:** valutati se per `SessionKey` esiste uno stato attivo. Se l'input non corrisponde al tipo atteso nello stato corrente, viene invocato un eventuale `@OnStateMismatch` senza alterare lo stato.
3. **Handler stateless standard:** valutati solo quando `currentState()` è vuoto.
4. **Serializzazione per `SessionKey`:** per prevenire race condition dovute a doppi tap ravvicinati su bottoni inline, il dispatcher serializza l'esecuzione per singola `SessionKey` (coda reattiva in-memory o lock distribuito su Redis).

### Storage Pluggable (`InMemorySessionStore` vs `RedisSessionStore`)

```java
public record SessionSnapshot(String state, String payloadJson, String payloadType) {}

public interface SessionStore {
    Uni<Optional<SessionSnapshot>> load(SessionKey key);
    Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl);
    Uni<Void> delete(SessionKey key);
}
```

#### Implementazione 1: In-Memory (`@DefaultBean`, zero dipendenze)
```java
@ApplicationScoped
@DefaultBean
public class InMemorySessionStore implements SessionStore {

    private record Entry(SessionSnapshot snapshot, Instant expiresAt) {}
    private final ConcurrentHashMap<SessionKey, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Optional<SessionSnapshot>> load(SessionKey key) {
        Entry entry = store.get(key);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            store.remove(key);
            return Uni.createFrom().item(Optional.empty());
        }
        return Uni.createFrom().item(Optional.of(entry.snapshot()));
    }

    @Override
    public Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl) {
        store.put(key, new Entry(snapshot, Instant.now().plus(ttl)));
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> delete(SessionKey key) {
        store.remove(key);
        return Uni.createFrom().voidItem();
    }
}
```

#### Implementazione 2: Redis Reattivo (`quarkus-redis-client`)
```java
@ApplicationScoped
@IfBuildProperty(name = "quarkus.telegram.session.store", stringValue = "redis")
public class RedisSessionStore implements SessionStore {

    private final ReactiveValueCommands<String, String> commands;
    private final ObjectMapper mapper;

    @Inject
    public RedisSessionStore(ReactiveRedisDataSource ds, ObjectMapper mapper) {
        this.commands = ds.value(String.class);
        this.mapper = mapper;
    }

    @Override
    public Uni<Optional<SessionSnapshot>> load(SessionKey key) {
        return commands.get(key.toStorageKey())
                .map(raw -> raw == null ? Optional.empty() : Optional.of(deserialize(raw)));
    }

    @Override
    public Uni<Void> save(SessionKey key, SessionSnapshot snapshot, Duration ttl) {
        return commands.psetex(key.toStorageKey(), ttl.toMillis(), serialize(snapshot));
    }

    @Override
    public Uni<Void> delete(SessionKey key) {
        return commands.getdel(key.toStorageKey()).replaceWithVoid();
    }
    // ... serialize / deserialize via ObjectMapper
}
```

---

## 8. Build-Time Processing nel Modulo `deployment`

`TelegramBotProcessor` sfrutta l'indice **Jandex** a compilazione per:
1. Registrare automaticamente tutti i DTO generati e i record `@SessionPayload` per la serializzazione Jackson su GraalVM (`ReflectiveClassBuildItem`).
2. Validare *fail-fast* a build-time le firme dei metodi annotati (parametri e tipi di ritorno supportati) e rilevare conflitti nel grafo degli stati `@OnState`.
3. Abilitare nativamente TLS/SSL (`ExtensionSslNativeSupportBuildItem`) per le chiamate HTTPS verso `api.telegram.org`.
4. Registrare la rotta HTTP Vert.x per il Webhook tramite `@Record(ExecutionTime.RUNTIME_INIT)`.

```java
package io.quarkiverse.telegram.bot.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.MethodInfo;

import java.util.List;

public class TelegramBotProcessor {

    private static final String FEATURE = "telegram-bot";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem enableSsl() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep
    void registerModelAndPayloadsForReflection(
            CombinedIndexBuildItem index,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // 1. Tutti i DTO generati nel package runtime.model
        index.getIndex().getKnownClasses().stream()
             .filter(c -> c.name().packagePrefix().startsWith("io.quarkiverse.telegram.bot.runtime.model"))
             .forEach(c -> reflectiveClasses.produce(
                 ReflectiveClassBuildItem.builder(c.name().toString())
                     .constructors().methods().fields().build()
             ));

        // 2. Record personalizzati annotati con @SessionPayload
        for (AnnotationInstance ann : index.getIndex().getAnnotations(SESSION_PAYLOAD)) {
            reflectiveClasses.produce(
                ReflectiveClassBuildItem.builder(ann.target().asClass().name().toString())
                    .constructors().methods().fields().build()
            );
        }
    }

    @BuildStep
    void scanHandlers(
            CombinedIndexBuildItem index,
            BuildProducer<HandlerBuildItem> handlers,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods) {

        for (AnnotationInstance ann : index.getIndex().getAnnotations(TELEGRAM_COMMAND)) {
            MethodInfo method = ann.target().asMethod();
            validateMethodSignature(method);
            reflectiveMethods.produce(new ReflectiveMethodBuildItem(method));

            handlers.produce(new HandlerBuildItem(
                HandlerType.COMMAND,
                ann.value().asString(),
                method.declaringClass().name().toString(),
                method.name(),
                extractParameterTypes(method)
            ));
        }
        // Scansione analoga per @OnCallbackQuery, @OnMessage e @OnState
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void setupTransport(
            TelegramBotRecorder recorder,
            TelegramBotConfig config,
            List<HandlerBuildItem> handlers,
            BuildProducer<RouteBuildItem> routes) {

        RuntimeValue<UpdateDispatcher> dispatcher = recorder.createDispatcher(handlers);

        routes.produce(RouteBuildItem.builder()
                .route(config.webhook().path())
                .handler(recorder.webhookHandler(dispatcher, config))
                .build());
    }
}
```

---

## 9. Strategia di Sviluppo, Testing e Roadmap

### Bootstrap su repository personale
1. **Generazione scheletro:** inizializzare il progetto tramite `quarkus create extension` (o `mvn io.quarkus:quarkus-maven-plugin:create-extension`) mantenendo fin da subito i package `io.quarkiverse.telegram.bot.*` e le dipendenze allineate al `quarkus-bom`. Questo rende l'eventuale migrazione futura verso `github.com/quarkiverse/quarkiverse` un semplice cambio di `<parent>` nel POM.
2. **Vertical Slice MVP (Milestone 1):**
   * Creare manualmente 3 record essenziali (`Update`, `Message`, `SendMessage`).
   * Implementare `TelegramBotApi` (`sendMessage`, `getUpdates`), `PollingRunner` e `@TelegramCommand`.
   * Compilare immediatamente con `-Dnative` (o tramite container Mandrel) e verificare l'esecuzione end-to-end contro le API reali di Telegram.
3. **Espansione incrementale:**
   * **Milestone 2:** Introduzione del modulo `codegen` con JavaPoet per generare l'intero set di DTO e `sealed interface` dallo schema OpenAPI.
   * **Milestone 3:** Implementazione del canale `MultipartForm` per `InputFile` (`sendPhoto`, `sendDocument`, `sendAudio`, `sendMediaGroup`).
   * **Milestone 4:** Implementazione del motore FSM (`@OnState`, `ConversationContext`, `InMemorySessionStore` e `RedisSessionStore`).
4. **Suite `integration-tests` (JVM & Native):**
   * Utilizzare **WireMock** (`QuarkusTestResourceLifecycleManager`) puntando `quarkus.telegram.client.base-url` al mock server per testare sia il loop di polling che gli endpoint webhook e multipart senza dipendere dalla rete.
   * Sfruttare **Quarkus DevServices per Redis** per validare la persistenza distribuita delle sessioni anche durante l'esecuzione `@QuarkusIntegrationTest` sul binario nativo.
