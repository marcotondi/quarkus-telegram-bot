package io.quarkiverse.telegram.bot.deployment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkiverse.telegram.bot.runtime.TelegramBotRecorder;
import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.OnState;
import io.quarkiverse.telegram.bot.runtime.api.OnStateMismatch;
import io.quarkiverse.telegram.bot.runtime.api.SessionPayload;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotBuildTimeConfig;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerDescriptor;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerType;
import io.quarkiverse.telegram.bot.runtime.dispatcher.UpdateDispatcher;
import io.quarkiverse.telegram.bot.runtime.model.BotCommand;
import io.quarkiverse.telegram.bot.runtime.session.InMemorySessionStore;
import io.quarkiverse.telegram.bot.runtime.session.RedisSessionStore;
import io.quarkiverse.telegram.bot.runtime.transport.PollingRunner;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.vertx.http.deployment.BodyHandlerBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;

class TelegramBotProcessor {

    private static final Logger LOG = Logger.getLogger(TelegramBotProcessor.class);
    private static final String FEATURE = "telegram-bot";

    private static final DotName TELEGRAM_COMMAND = DotName.createSimple(TelegramCommand.class.getName());
    private static final DotName ON_CALLBACK_QUERY = DotName.createSimple(OnCallbackQuery.class.getName());
    private static final DotName ON_MESSAGE = DotName.createSimple(OnMessage.class.getName());
    private static final DotName ON_STATE = DotName.createSimple(OnState.class.getName());
    private static final DotName ON_STATE_MISMATCH = DotName.createSimple(OnStateMismatch.class.getName());
    private static final DotName SESSION_PAYLOAD = DotName.createSimple(SessionPayload.class.getName());

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    IndexDependencyBuildItem indexRuntime() {
        return new IndexDependencyBuildItem("io.quarkiverse.telegram", "quarkus-telegram-bot");
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem enableSsl() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    @BuildStep
    void clientConfigDefaults(BuildProducer<RunTimeConfigurationDefaultBuildItem> defaults) {
        defaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "quarkus.rest-client.telegram-api.url",
                "${quarkus.telegram.client.base-url:https://api.telegram.org}"));
    }

    @BuildStep
    void additionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(TelegramBotClient.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(InMemorySessionStore.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RedisSessionStore.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(PollingRunner.class));
    }

    @BuildStep
    void registerModelAndPayloadsForReflection(
            CombinedIndexBuildItem index,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {

        // 1. All model DTOs in runtime.model
        for (ClassInfo clazz : index.getIndex().getKnownClasses()) {
            if (clazz.name().packagePrefix().startsWith("io.quarkiverse.telegram.bot.runtime.model")) {
                reflectiveClasses.produce(ReflectiveClassBuildItem.builder(clazz.name().toString())
                        .constructors().methods().fields().build());
            }
        }

        // 2. Custom records annotated with @SessionPayload
        for (AnnotationInstance ann : index.getIndex().getAnnotations(SESSION_PAYLOAD)) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(ann.target().asClass().name().toString())
                    .constructors().methods().fields().build());
        }
    }

    @BuildStep
    void scanHandlers(
            CombinedIndexBuildItem index,
            BuildProducer<HandlerBuildItem> handlers,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {

        Set<String> botClasses = new HashSet<>();

        // Scan @TelegramCommand
        for (AnnotationInstance ann : index.getIndex().getAnnotations(TELEGRAM_COMMAND)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String command = ann.value().asString();
            String description = ann.value("description") != null ? ann.value("description").asString() : null;
            boolean interrupts = ann.value("interruptsState") != null && ann.value("interruptsState").asBoolean();
            String state = getStateCondition(method);

            handlers.produce(new HandlerBuildItem(new HandlerDescriptor(
                    HandlerType.COMMAND,
                    command,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    interrupts,
                    state,
                    null,
                    description)));
        }

        // Scan @OnCallbackQuery
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_CALLBACK_QUERY)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String pattern = ann.value() != null ? ann.value().asString() : "*";
            String state = getStateCondition(method);

            handlers.produce(new HandlerBuildItem(new HandlerDescriptor(
                    HandlerType.CALLBACK_QUERY,
                    pattern,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    state,
                    null)));
        }

        // Scan @OnMessage
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_MESSAGE)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            AnnotationValue typeVal = ann.value("type");
            String messageType = typeVal != null ? typeVal.asEnum() : MessageType.ANY.name();
            String state = getStateCondition(method);

            handlers.produce(new HandlerBuildItem(new HandlerDescriptor(
                    HandlerType.MESSAGE,
                    null,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    state,
                    messageType)));
        }

        // Scan @OnStateMismatch
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_STATE_MISMATCH)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String state = ann.value() != null ? ann.value().asString() : "";
            handlers.produce(new HandlerBuildItem(new HandlerDescriptor(
                    HandlerType.STATE_MISMATCH,
                    null,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    state.isEmpty() ? null : state,
                    null)));
        }

        unremovableBeans
                .produce(new UnremovableBeanBuildItem(beanInfo -> botClasses.contains(beanInfo.getBeanClass().toString())));
    }

    private void registerMethodAndClass(
            MethodInfo method,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        reflectiveMethods.produce(new ReflectiveMethodBuildItem(method));
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(method.declaringClass().name().toString())
                .constructors().methods().fields().build());
    }

    private String getStateCondition(MethodInfo method) {
        AnnotationInstance stateAnn = method.annotation(ON_STATE);
        return stateAnn != null ? stateAnn.value().asString() : null;
    }

    private List<String> extractParameterTypes(MethodInfo method) {
        List<String> list = new ArrayList<>();
        for (Type t : method.parameterTypes()) {
            list.add(t.name().toString());
        }
        return list;
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void setupTransports(
            TelegramBotRecorder recorder,
            TelegramBotBuildTimeConfig buildTimeConfig,
            LaunchModeBuildItem launchMode,
            List<HandlerBuildItem> handlerBuildItems,
            BuildProducer<RouteBuildItem> routes,
            BodyHandlerBuildItem bodyHandler) {

        List<HandlerDescriptor> descriptors = new ArrayList<>();
        List<BotCommand> botCommands = new ArrayList<>();

        for (HandlerBuildItem item : handlerBuildItems) {
            descriptors.add(item.getDescriptor());
            if (item.getDescriptor().getType() == HandlerType.COMMAND
                    && item.getDescriptor().getDescription() != null
                    && !item.getDescriptor().getDescription().isBlank()) {
                String cmd = item.getDescriptor().getMatchValue();
                if (cmd != null && cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }
                botCommands.add(new BotCommand(cmd, item.getDescriptor().getDescription()));
            }
        }

        RuntimeValue<UpdateDispatcher> dispatcher = recorder.createDispatcher(descriptors);

        // Register Webhook route on Vert.x HTTP with bodyHandler if enabled
        if (buildTimeConfig.webhook().enabled()) {
            routes.produce(RouteBuildItem.builder()
                    .orderedRoute(buildTimeConfig.webhook().path(), 100)
                    .handler(bodyHandler.getHandler())
                    .build());

            routes.produce(RouteBuildItem.builder()
                    .orderedRoute(buildTimeConfig.webhook().path(), 200)
                    .handler(recorder.createWebhookHandler(dispatcher))
                    .build());
        }

        // Initialize transports (polling or webhook)
        recorder.initTransports(dispatcher, launchMode.getLaunchMode(), botCommands, buildTimeConfig.webhook().path());
    }
}
