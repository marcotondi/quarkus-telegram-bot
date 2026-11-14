package io.quarkiverse.telegram.bot.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.telegram.bot.runtime.client.TelegramBotClient;
import io.quarkiverse.telegram.bot.runtime.model.AnswerInlineQuery;
import io.quarkiverse.telegram.bot.runtime.model.ChosenInlineResult;
import io.quarkiverse.telegram.bot.runtime.model.InlineQueryResultArticle;
import io.quarkiverse.telegram.bot.runtime.model.InputTextMessageContent;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@QuarkusTestResource(TelegramMockServer.class)
public class TelegramInlineQueryTest {

    @Inject
    TelegramBotClient client;

    @Test
    public void testInlineQueryReturnsListResults() throws Exception {
        String updateJson = """
                {
                    "update_id": 801,
                    "inline_query": {
                        "id": "iq_100",
                        "from": {
                            "id": 9999,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "query": "search_quarkus",
                        "offset": ""
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        Thread.sleep(100);

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerInlineQuery"))
                .withRequestBody(WireMock.matchingJsonPath("$.inline_query_id", WireMock.equalTo("iq_100")))
                .withRequestBody(WireMock.matchingJsonPath("$.results[0].id", WireMock.equalTo("search_1")))
                .withRequestBody(WireMock.matchingJsonPath("$.results[0].title", WireMock.equalTo("Search Result for quarkus")))
                .withRequestBody(WireMock.matchingJsonPath("$.results[0].input_message_content.message_text",
                        WireMock.equalTo("Found: quarkus"))));
    }

    @Test
    public void testInlineQueryReturnsAnswerInlineQueryCustom() throws Exception {
        String updateJson = """
                {
                    "update_id": 802,
                    "inline_query": {
                        "id": "iq_101",
                        "from": {
                            "id": 9999,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "query": "custom_test",
                        "offset": ""
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerInlineQuery"))
                .withRequestBody(WireMock.matchingJsonPath("$.inline_query_id", WireMock.equalTo("iq_101")))
                .withRequestBody(WireMock.matchingJsonPath("$.cache_time", WireMock.equalTo("60")))
                .withRequestBody(WireMock.matchingJsonPath("$.is_personal", WireMock.equalTo("true")))
                .withRequestBody(WireMock.matchingJsonPath("$.results[0].id", WireMock.equalTo("custom_1"))));
    }

    @Test
    public void testChosenInlineResult() {
        SampleBot.LAST_CHOSEN_RESULT.set(null);

        String updateJson = """
                {
                    "update_id": 803,
                    "chosen_inline_result": {
                        "result_id": "res_picked_123",
                        "from": {
                            "id": 9999,
                            "is_bot": false,
                            "first_name": "Mario"
                        },
                        "query": "search_quarkus"
                    }
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(updateJson)
                .when()
                .post("/telegram/webhook")
                .then()
                .statusCode(200);

        ChosenInlineResult chosen = SampleBot.LAST_CHOSEN_RESULT.get();
        assertNotNull(chosen);
        assertEquals("res_picked_123", chosen.resultId());
        assertEquals("search_quarkus", chosen.query());
        assertEquals(9999L, chosen.from().id());
    }

    @Test
    public void testClientDirectAnswerInlineQuery() throws Exception {
        AnswerInlineQuery request = AnswerInlineQuery.builder()
                .inlineQueryId("direct_iq_456")
                .results(List.of(
                        InlineQueryResultArticle.builder()
                                .id("direct_1")
                                .type("article")
                                .title("Direct Article")
                                .inputMessageContent(new InputTextMessageContent("Direct Content", null, null, null))
                                .build()))
                .build();

        Boolean ok = client.answerInlineQuery(request).await().indefinitely();
        assertTrue(ok);

        verifyWithRetry(WireMock.postRequestedFor(WireMock.urlMatching("/bot.*/answerInlineQuery"))
                .withRequestBody(WireMock.matchingJsonPath("$.inline_query_id", WireMock.equalTo("direct_iq_456")))
                .withRequestBody(WireMock.matchingJsonPath("$.results[0].id", WireMock.equalTo("direct_1"))));
    }

    private void verifyWithRetry(com.github.tomakehurst.wiremock.matching.RequestPatternBuilder pattern)
            throws InterruptedException {
        long end = System.currentTimeMillis() + 3000;
        AssertionError lastErr = null;
        while (System.currentTimeMillis() < end) {
            try {
                TelegramMockServer.getWireMockServer().verify(pattern);
                return;
            } catch (AssertionError e) {
                lastErr = e;
                Thread.sleep(100);
            }
        }
        if (lastErr != null) {
            throw lastErr;
        }
    }
}
