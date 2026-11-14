package io.quarkiverse.telegram.bot.deployment.devui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.telegram.bot.deployment.HandlerBuildItem;
import io.quarkiverse.telegram.bot.runtime.config.TelegramBotBuildTimeConfig;
import io.quarkiverse.telegram.bot.runtime.devui.TelegramBotJsonRPCService;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerDescriptor;
import io.quarkiverse.telegram.bot.runtime.dispatcher.HandlerType;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.CardText;
import io.quarkus.devui.spi.page.Page;

public class TelegramBotDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    public void createJsonRpc(
            BuildProducer<JsonRPCProvidersBuildItem> jsonRpcProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(TelegramBotJsonRPCService.class));
        jsonRpcProducer.produce(new JsonRPCProvidersBuildItem(TelegramBotJsonRPCService.class));
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    public CardPageBuildItem createDevUI(
            TelegramBotBuildTimeConfig buildTimeConfig,
            List<HandlerBuildItem> handlers) {

        CardPageBuildItem card = new CardPageBuildItem();

        // 1. Build time data for Handlers table and FSM table
        List<DevUIHandlerEntry> handlerEntries = new ArrayList<>();
        List<DevUIFsmEntry> fsmEntries = new ArrayList<>();

        for (HandlerBuildItem item : handlers) {
            HandlerDescriptor d = item.getDescriptor();
            handlerEntries.add(toHandlerEntry(d));
            if (d.getStateCondition() != null || d.isInterruptsState() || d.getType() == HandlerType.STATE_MISMATCH) {
                fsmEntries.add(toFsmEntry(d));
            }
        }

        // Add build-time data to CardPageBuildItem
        card.addBuildTimeData("handlers", handlerEntries);
        card.addBuildTimeData("fsmStates", fsmEntries);

        // Add Pages (tabs under Telegram Bot card)
        card.addPage(Page.tableDataPageBuilder("Handlers & Routing")
                .icon("font-awesome-solid:terminal")
                .staticLabel(String.valueOf(handlerEntries.size()))
                .buildTimeDataKey("handlers")
                .showColumn("type")
                .showColumn("match")
                .showColumn("targetMethod")
                .showColumn("chatTypes")
                .showColumn("security")
                .showColumn("state")
                .showColumn("description"));

        card.addPage(Page.tableDataPageBuilder("FSM States & Transitions")
                .icon("font-awesome-solid:diagram-project")
                .staticLabel(String.valueOf(fsmEntries.size()))
                .buildTimeDataKey("fsmStates")
                .showColumn("state")
                .showColumn("trigger")
                .showColumn("handler")
                .showColumn("interruptsState")
                .showColumn("description"));

        // 2. Card text summaries
        Config config = ConfigProvider.getConfig();
        String botUsername = config.getOptionalValue("quarkus.telegram.bot-username", String.class).orElse(null);
        String mode = config.getOptionalValue("quarkus.telegram.mode", String.class).orElse("AUTO");
        boolean hasToken = config.getOptionalValue("quarkus.telegram.token", String.class)
                .filter(t -> !t.isBlank())
                .isPresent();

        card.addText(CardText.textBuilder()
                .title("Bot Identity")
                .icon("font-awesome-solid:robot")
                .staticText(botUsername != null ? "@" + botUsername : "(not configured)")
                .dynamicText("getBotUsername")
                .build());

        card.addText(CardText.textBuilder()
                .title("Transport Mode")
                .icon("font-awesome-solid:satellite-dish")
                .staticText(mode)
                .dynamicText("getTransportMode")
                .build());

        card.addText(CardText.textBuilder()
                .title("Token Status")
                .icon("font-awesome-solid:key")
                .staticText(hasToken ? "Configured" : "Missing")
                .dynamicText("getTokenStatus")
                .build());

        card.addText(CardText.textBuilder()
                .title("Session Store")
                .icon("font-awesome-solid:database")
                .staticText(buildTimeConfig.session().store())
                .build());

        card.addText(CardText.textBuilder()
                .title("Webhook Route")
                .icon("font-awesome-solid:globe")
                .staticText(buildTimeConfig.webhook().enabled() ? buildTimeConfig.webhook().path() : "Disabled")
                .build());

        return card;
    }

    public static DevUIHandlerEntry toHandlerEntry(HandlerDescriptor d) {
        String type = d.getType() != null ? d.getType().name() : "UNKNOWN";
        String match = "-";
        if (d.getMatchValue() != null && !d.getMatchValue().isBlank()) {
            match = d.getMatchValue();
        } else if (d.getMessageType() != null) {
            match = d.getMessageType();
        } else if (d.getType() == HandlerType.STATE_MISMATCH) {
            match = d.getStateCondition() != null ? d.getStateCondition() : "(any)";
        } else if (d.getType() == HandlerType.BEFORE_UPDATE || d.getType() == HandlerType.AFTER_UPDATE) {
            match = "priority=" + d.getPriority();
        }

        String target = simpleClassName(d.getClassName()) + "#" + d.getMethodName();

        String chatTypes = (d.getAllowedChatTypes() == null || d.getAllowedChatTypes().isEmpty())
                ? "ALL"
                : String.join(", ", d.getAllowedChatTypes());

        String security;
        if (d.isAdminOnly()) {
            security = "@BotAdmin" + (d.isSilentSecurity() ? " (silent)" : "");
        } else if (d.getRequiredRoles() != null && !d.getRequiredRoles().isEmpty()) {
            security = "roles=" + d.getRequiredRoles();
        } else if (d.getAllowedUsers() != null && !d.getAllowedUsers().isEmpty()) {
            security = "users=" + d.getAllowedUsers();
        } else {
            security = "public";
        }

        String state = d.getStateCondition() != null ? d.getStateCondition() : "-";
        String description = d.getDescription() != null && !d.getDescription().isBlank() ? d.getDescription() : "-";

        return new DevUIHandlerEntry(type, match, target, chatTypes, security, state, description);
    }

    public static DevUIFsmEntry toFsmEntry(HandlerDescriptor d) {
        String state = d.getStateCondition() != null ? d.getStateCondition() : "(any state)";
        String trigger;
        if (d.getType() == null) {
            trigger = "UNKNOWN";
        } else {
            switch (d.getType()) {
                case COMMAND -> trigger = "COMMAND (" + d.getMatchValue() + ")";
                case CALLBACK_QUERY -> trigger = "CALLBACK (" + d.getMatchValue() + ")";
                case MESSAGE -> trigger = "MESSAGE (" + (d.getMessageType() != null ? d.getMessageType() : "ANY") + ")";
                case STATE_MISMATCH -> trigger = "STATE_MISMATCH";
                default -> trigger = d.getType().name();
            }
        }

        String handler = simpleClassName(d.getClassName()) + "#" + d.getMethodName();
        String interrupts = d.isInterruptsState() ? "YES" : "NO";
        String description = d.getDescription() != null && !d.getDescription().isBlank() ? d.getDescription() : "-";

        return new DevUIFsmEntry(state, trigger, handler, interrupts, description);
    }

    static String simpleClassName(String fullClassName) {
        if (fullClassName == null) {
            return "";
        }
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
}
