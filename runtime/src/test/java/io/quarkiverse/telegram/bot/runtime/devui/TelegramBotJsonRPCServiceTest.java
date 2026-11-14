package io.quarkiverse.telegram.bot.runtime.devui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.telegram.bot.runtime.config.TelegramBotConfig;

public class TelegramBotJsonRPCServiceTest {

    private TelegramBotConfig config;
    private TelegramBotJsonRPCService service;

    @BeforeEach
    public void setup() {
        config = mock(TelegramBotConfig.class);
        service = new TelegramBotJsonRPCService();
        service.config = config;
    }

    @Test
    public void testBotInfoWithConfiguredBot() {
        when(config.botUsername()).thenReturn(Optional.of("SampleBot"));
        when(config.mode()).thenReturn(TelegramBotConfig.TransportMode.POLLING);
        when(config.token()).thenReturn(Optional.of("123456:SECRET"));

        assertEquals("@SampleBot", service.getBotUsername());
        assertEquals("POLLING", service.getTransportMode());
        assertEquals("Configured", service.getTokenStatus());

        TelegramBotJsonRPCService.BotInfo info = service.getBotInfo();
        assertNotNull(info);
        assertEquals("@SampleBot", info.username());
        assertEquals("POLLING", info.mode());
        assertEquals("Configured", info.tokenStatus());
    }

    @Test
    public void testBotInfoWithDefaults() {
        when(config.botUsername()).thenReturn(Optional.empty());
        when(config.mode()).thenReturn(TelegramBotConfig.TransportMode.WEBHOOK);
        when(config.token()).thenReturn(Optional.empty());

        assertEquals("(not configured)", service.getBotUsername());
        assertEquals("WEBHOOK", service.getTransportMode());
        assertEquals("Missing", service.getTokenStatus());
    }
}
