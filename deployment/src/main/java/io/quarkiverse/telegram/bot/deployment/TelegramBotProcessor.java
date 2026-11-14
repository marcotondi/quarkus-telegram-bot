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
import io.quarkiverse.telegram.bot.runtime.TelegramBotStatus;
import io.quarkiverse.telegram.bot.runtime.api.AfterUpdate;
import io.quarkiverse.telegram.bot.runtime.api.BeforeUpdate;
import io.quarkiverse.telegram.bot.runtime.api.BotAdmin;
import io.quarkiverse.telegram.bot.runtime.api.ChatType;
import io.quarkiverse.telegram.bot.runtime.api.MessageType;
import io.quarkiverse.telegram.bot.runtime.api.OnCallbackQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.api.OnError;
import io.quarkiverse.telegram.bot.runtime.api.OnInlineQuery;
import io.quarkiverse.telegram.bot.runtime.api.OnMessage;
import io.quarkiverse.telegram.bot.runtime.api.OnState;
import io.quarkiverse.telegram.bot.runtime.api.OnStateMismatch;
import io.quarkiverse.telegram.bot.runtime.api.SecuredBot;
import io.quarkiverse.telegram.bot.runtime.api.SessionPayload;
import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.api.TelegramExceptionHandler;
import io.quarkiverse.telegram.bot.runtime.api.TelegramRoleResolver;
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
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
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
    private static final DotName ON_INLINE_QUERY = DotName.createSimple(OnInlineQuery.class.getName());
    private static final DotName ON_CHOSEN_INLINE_RESULT = DotName.createSimple(OnChosenInlineResult.class.getName());
    private static final DotName ON_ERROR = DotName.createSimple(OnError.class.getName());
    private static final DotName TELEGRAM_EXCEPTION_HANDLER = DotName.createSimple(TelegramExceptionHandler.class.getName());
    private static final String HEALTH_CHECK_CLASS = "io.quarkiverse.telegram.bot.runtime.health.TelegramBotHealthCheck";
    private static final String METRICS_CLASS = "io.quarkiverse.telegram.bot.runtime.metrics.TelegramBotMetrics";

    private static final DotName SESSION_PAYLOAD = DotName.createSimple(SessionPayload.class.getName());
    private static final DotName CHAT_TYPE = DotName.createSimple(ChatType.class.getName());
    private static final DotName BOT_ADMIN = DotName.createSimple(BotAdmin.class.getName());
    private static final DotName SECURED_BOT = DotName.createSimple(SecuredBot.class.getName());
    private static final DotName TELEGRAM_ROLE_RESOLVER = DotName.createSimple(TelegramRoleResolver.class.getName());
    private static final DotName BEFORE_UPDATE = DotName.createSimple(BeforeUpdate.class.getName());
    private static final DotName AFTER_UPDATE = DotName.createSimple(AfterUpdate.class.getName());

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
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(TelegramBotStatus.class));
    }

    @BuildStep
    void optionalIntegrationBeans(
            Capabilities capabilities,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<ExcludedTypeBuildItem> excludedTypes) {
        // The runtime jar is a bean archive: keep the optional integrations out of discovery unless their extension is present.
        // Class names are used instead of class literals so this step never loads classes that need the optional API.
        boolean health = capabilities.isPresent(Capability.SMALLRYE_HEALTH);
        boolean metrics = capabilities.isPresent("io.quarkus.metrics");
        if (health) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder().addBeanClass(HEALTH_CHECK_CLASS).setUnremovable()
                    .build());
        } else {
            excludedTypes.produce(new ExcludedTypeBuildItem(HEALTH_CHECK_CLASS));
        }
        if (metrics) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder().addBeanClass(METRICS_CLASS).setUnremovable().build());
        } else {
            excludedTypes.produce(new ExcludedTypeBuildItem(METRICS_CLASS));
        }
    }

    @BuildStep
    @Produce(ArtifactResultBuildItem.class)
    void validateHandlers(CombinedIndexBuildItem index, List<HandlerBuildItem> handlers) {
        HandlerValidator.validateSignatures(index.getIndex());
        HandlerValidator.validateNoDuplicateCommands(handlers);
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

            HandlerDescriptor desc = new HandlerDescriptor(
                    HandlerType.COMMAND,
                    command,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    interrupts,
                    state,
                    null,
                    description);
            applySecurityAndFilters(desc, method);
            handlers.produce(new HandlerBuildItem(desc));
        }

        // Scan @OnCallbackQuery
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_CALLBACK_QUERY)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String pattern = ann.value() != null ? ann.value().asString() : "*";
            String state = getStateCondition(method);

            HandlerDescriptor desc = new HandlerDescriptor(
                    HandlerType.CALLBACK_QUERY,
                    pattern,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    state,
                    null);
            applySecurityAndFilters(desc, method);
            handlers.produce(new HandlerBuildItem(desc));
        }

        // Scan @OnMessage
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_MESSAGE)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            AnnotationValue typeVal = ann.value("type");
            String messageType = typeVal != null ? typeVal.asEnum() : MessageType.ANY.name();
            String state = getStateCondition(method);

            HandlerDescriptor desc = new HandlerDescriptor(
                    HandlerType.MESSAGE,
                    null,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    state,
                    messageType);
            applySecurityAndFilters(desc, method);
            handlers.produce(new HandlerBuildItem(desc));
        }

        // Scan @OnStateMismatch
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_STATE_MISMATCH)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String state = ann.value() != null ? ann.value().asString() : "";
            HandlerDescriptor desc = new HandlerDescriptor(
                    HandlerType.STATE_MISMATCH,
                    null,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    state.isEmpty() ? null : state,
                    null);
            applySecurityAndFilters(desc, method);
            handlers.produce(new HandlerBuildItem(desc));
        }

        // Scan @OnInlineQuery
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_INLINE_QUERY)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String pattern = ann.value() != null ? ann.value().asString() : "*";
            HandlerDescriptor desc = new HandlerDescriptor(
                    HandlerType.INLINE_QUERY,
                    pattern,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    null,
                    null);
            applySecurityAndFilters(desc, method);
            handlers.produce(new HandlerBuildItem(desc));
        }

        // Scan @OnChosenInlineResult
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_CHOSEN_INLINE_RESULT)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            String pattern = ann.value() != null ? ann.value().asString() : "*";
            HandlerDescriptor desc = new HandlerDescriptor(
                    HandlerType.CHOSEN_INLINE_RESULT,
                    pattern,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    null,
                    null);
            applySecurityAndFilters(desc, method);
            handlers.produce(new HandlerBuildItem(desc));
        }

        // Scan @BeforeUpdate & @AfterUpdate interceptors
        scanInterceptors(index, BEFORE_UPDATE, HandlerType.BEFORE_UPDATE, reflectiveMethods, reflectiveClasses, botClasses,
                handlers);
        scanInterceptors(index, AFTER_UPDATE, HandlerType.AFTER_UPDATE, reflectiveMethods, reflectiveClasses, botClasses,
                handlers);

        // Scan @OnError
        for (AnnotationInstance ann : index.getIndex().getAnnotations(ON_ERROR)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            AnnotationValue val = ann.value();
            String exType = val != null ? val.asClass().name().toString() : Throwable.class.getName();

            handlers.produce(new HandlerBuildItem(new HandlerDescriptor(
                    HandlerType.ERROR,
                    exType,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method),
                    false,
                    null,
                    null)));
        }

        // Scan TelegramExceptionHandler implementors
        for (ClassInfo clazz : index.getIndex().getAllKnownImplementors(TELEGRAM_EXCEPTION_HANDLER)) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(clazz.name().toString())
                    .constructors().methods().fields().build());
            botClasses.add(clazz.name().toString());

            for (MethodInfo method : clazz.methods()) {
                if (method.name().equals("handle") && method.parametersCount() == 3) {
                    String exType = method.parameterType(0).name().toString();
                    handlers.produce(new HandlerBuildItem(new HandlerDescriptor(
                            HandlerType.ERROR,
                            exType,
                            clazz.name().toString(),
                            method.name(),
                            extractParameterTypes(method),
                            false,
                            null,
                            null)));
                }
            }
        }

        // Scan TelegramRoleResolver implementors
        for (ClassInfo clazz : index.getIndex().getAllKnownImplementors(TELEGRAM_ROLE_RESOLVER)) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(clazz.name().toString())
                    .constructors().methods().fields().build());
            botClasses.add(clazz.name().toString());
        }

        unremovableBeans
                .produce(new UnremovableBeanBuildItem(beanInfo -> botClasses.contains(beanInfo.getBeanClass().toString())));
    }

    private void applySecurityAndFilters(HandlerDescriptor descriptor, MethodInfo method) {
        // 1. ChatType
        AnnotationInstance chatTypeAnn = method.annotation(CHAT_TYPE);
        if (chatTypeAnn == null) {
            chatTypeAnn = method.declaringClass().declaredAnnotation(CHAT_TYPE);
        }
        if (chatTypeAnn != null) {
            AnnotationValue val = chatTypeAnn.value();
            if (val != null) {
                List<String> types = new ArrayList<>();
                if (val.kind() == AnnotationValue.Kind.ARRAY) {
                    for (String s : val.asEnumArray()) {
                        types.add(s.toLowerCase());
                    }
                } else if (val.kind() == AnnotationValue.Kind.ENUM) {
                    types.add(val.asEnum().toLowerCase());
                }
                descriptor.setAllowedChatTypes(types);
            }
        }

        // 2. BotAdmin
        AnnotationInstance botAdminAnn = method.annotation(BOT_ADMIN);
        if (botAdminAnn == null) {
            botAdminAnn = method.declaringClass().declaredAnnotation(BOT_ADMIN);
        }
        if (botAdminAnn != null) {
            descriptor.setAdminOnly(true);
            AnnotationValue silentVal = botAdminAnn.value("silent");
            if (silentVal != null && silentVal.asBoolean()) {
                descriptor.setSilentSecurity(true);
            }
        }

        // 3. SecuredBot
        AnnotationInstance securedAnn = method.annotation(SECURED_BOT);
        if (securedAnn == null) {
            securedAnn = method.declaringClass().declaredAnnotation(SECURED_BOT);
        }
        if (securedAnn != null) {
            AnnotationValue rolesVal = securedAnn.value("roles");
            if (rolesVal != null) {
                descriptor.setRequiredRoles(List.of(rolesVal.asStringArray()));
            }
            AnnotationValue usersVal = securedAnn.value("allowedUsers");
            if (usersVal != null) {
                descriptor.setAllowedUsers(List.of(usersVal.asStringArray()));
            }
            AnnotationValue silentVal = securedAnn.value("silent");
            if (silentVal != null && silentVal.asBoolean()) {
                descriptor.setSilentSecurity(true);
            }
        }
    }

    private void scanInterceptors(
            CombinedIndexBuildItem index,
            DotName annotationName,
            HandlerType handlerType,
            BuildProducer<ReflectiveMethodBuildItem> reflectiveMethods,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            Set<String> botClasses,
            BuildProducer<HandlerBuildItem> handlers) {
        for (AnnotationInstance ann : index.getIndex().getAnnotations(annotationName)) {
            MethodInfo method = ann.target().asMethod();
            registerMethodAndClass(method, reflectiveMethods, reflectiveClasses);
            botClasses.add(method.declaringClass().name().toString());

            AnnotationValue priorityVal = ann.value("priority");
            int priority = priorityVal != null ? priorityVal.asInt() : 0;

            HandlerDescriptor desc = new HandlerDescriptor(
                    handlerType,
                    null,
                    method.declaringClass().name().toString(),
                    method.name(),
                    extractParameterTypes(method));
            desc.setPriority(priority);
            handlers.produce(new HandlerBuildItem(desc));
        }
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
        recorder.initTransports(dispatcher, launchMode.getLaunchMode(), botCommands, buildTimeConfig.webhook().path(),
                buildTimeConfig.webhook().enabled());
    }
}
