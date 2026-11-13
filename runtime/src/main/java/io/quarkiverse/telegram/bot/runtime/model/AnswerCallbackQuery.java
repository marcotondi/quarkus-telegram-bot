package io.quarkiverse.telegram.bot.runtime.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerCallbackQuery(
        @JsonProperty("callback_query_id") String callbackQueryId,
        @JsonProperty("text") String text,
        @JsonProperty("show_alert") Boolean showAlert,
        @JsonProperty("url") String url,
        @JsonProperty("cache_time") Integer cacheTime) {

    public AnswerCallbackQuery(String callbackQueryId) {
        this(callbackQueryId, null, null, null, null);
    }

    public AnswerCallbackQuery(String callbackQueryId, String text) {
        this(callbackQueryId, text, null, null, null);
    }

    public AnswerCallbackQuery(String callbackQueryId, String text, boolean showAlert) {
        this(callbackQueryId, text, showAlert, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String callbackQueryId;
        private String text;
        private Boolean showAlert;
        private String url;
        private Integer cacheTime;

        public Builder callbackQueryId(String callbackQueryId) {
            this.callbackQueryId = callbackQueryId;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder showAlert(boolean showAlert) {
            this.showAlert = showAlert;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder cacheTime(int cacheTime) {
            this.cacheTime = cacheTime;
            return this;
        }

        public AnswerCallbackQuery build() {
            return new AnswerCallbackQuery(callbackQueryId, text, showAlert, url, cacheTime);
        }
    }
}
