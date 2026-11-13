package io.quarkiverse.telegram.bot.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.telegram.bot.runtime.api.TelegramCommand;
import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkus.arc.Arc;
import io.quarkus.test.QuarkusExtensionTest;

public class TelegramBotTest {

    @ApplicationScoped
    public static class TestPingBot {
        @TelegramCommand(value = "/ping", description = "Ping command")
        public String ping() {
            return "pong";
        }
    }

    @RegisterExtension
    static final QuarkusExtensionTest unitTest = new QuarkusExtensionTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClass(TestPingBot.class)
                    .addAsResource(new StringAsset(
                            "quarkus.telegram.token=123:test\nquarkus.telegram.mode=POLLING\nquarkus.telegram.polling.enabled=false\n"),
                            "application.properties"));

    @Test
    public void testBotBeanAndClientLoaded() {
        TelegramBotClient client = Arc.container().instance(TelegramBotClient.class).get();
        assertNotNull(client);

        TestPingBot bot = Arc.container().instance(TestPingBot.class).get();
        assertNotNull(bot);
        assertTrue(Arc.container().instance(TestPingBot.class).isAvailable());
    }
}
