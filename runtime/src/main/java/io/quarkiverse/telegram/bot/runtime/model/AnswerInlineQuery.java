package io.quarkiverse.telegram.bot.runtime.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for the /answerInlineQuery Telegram Bot API method.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerInlineQuery(
        @JsonProperty("inline_query_id") String inlineQueryId,
        @JsonProperty("results") List<InlineQueryResult> results,
        @JsonProperty("cache_time") Integer cacheTime,
        @JsonProperty("is_personal") Boolean isPersonal,
        @JsonProperty("next_offset") String nextOffset,
        @JsonProperty("button") InlineQueryResultsButton button) {

    public AnswerInlineQuery(String inlineQueryId, List<InlineQueryResult> results) {
        this(inlineQueryId, results, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String inlineQueryId;
        private List<InlineQueryResult> results;
        private Integer cacheTime;
        private Boolean isPersonal;
        private String nextOffset;
        private InlineQueryResultsButton button;

        public Builder inlineQueryId(String inlineQueryId) {
            this.inlineQueryId = inlineQueryId;
            return this;
        }

        public Builder results(List<InlineQueryResult> results) {
            this.results = results;
            return this;
        }

        public Builder cacheTime(Integer cacheTime) {
            this.cacheTime = cacheTime;
            return this;
        }

        public Builder isPersonal(Boolean isPersonal) {
            this.isPersonal = isPersonal;
            return this;
        }

        public Builder nextOffset(String nextOffset) {
            this.nextOffset = nextOffset;
            return this;
        }

        public Builder button(InlineQueryResultsButton button) {
            this.button = button;
            return this;
        }

        public AnswerInlineQuery build() {
            return new AnswerInlineQuery(inlineQueryId, results, cacheTime, isPersonal, nextOffset, button);
        }
    }
}
