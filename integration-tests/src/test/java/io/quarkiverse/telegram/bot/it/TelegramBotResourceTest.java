package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TelegramBotResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/telegram-bot")
                .then()
                .statusCode(200)
                .body(is("Hello telegram-bot"));
    }
}
