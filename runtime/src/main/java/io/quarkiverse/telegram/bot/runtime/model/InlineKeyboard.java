package io.quarkiverse.telegram.bot.runtime.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent helper for constructing {@link InlineKeyboardMarkup} instances easily.
 */
public final class InlineKeyboard {

    private InlineKeyboard() {
    }

    /**
     * Builds an {@link InlineKeyboardMarkup} with a single row of buttons from alternating (text, callbackData) arguments.
     *
     * @param textAndData alternating button label and callback query data
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup of(String... textAndData) {
        if (textAndData == null || textAndData.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be alternating pairs of (text, callbackData)");
        }
        List<InlineKeyboardButton> row = new ArrayList<>();
        for (int i = 0; i < textAndData.length; i += 2) {
            row.add(InlineKeyboardButton.builder()
                    .text(textAndData[i])
                    .callbackData(textAndData[i + 1])
                    .build());
        }
        return new InlineKeyboardMarkup(List.of(row));
    }

    /**
     * Builds an {@link InlineKeyboardMarkup} with a single column of buttons from alternating (text, callbackData) arguments.
     *
     * @param textAndData alternating button label and callback query data
     * @return InlineKeyboardMarkup
     */
    public static InlineKeyboardMarkup column(String... textAndData) {
        if (textAndData == null || textAndData.length % 2 != 0) {
            throw new IllegalArgumentException("Arguments must be alternating pairs of (text, callbackData)");
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < textAndData.length; i += 2) {
            rows.add(List.of(InlineKeyboardButton.builder()
                    .text(textAndData[i])
                    .callbackData(textAndData[i + 1])
                    .build()));
        }
        return new InlineKeyboardMarkup(rows);
    }
}
