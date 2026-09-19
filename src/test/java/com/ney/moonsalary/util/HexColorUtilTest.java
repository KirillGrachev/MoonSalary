package com.ney.moonsalary.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexColorUtilTest {

    private static final char SECTION = '\u00A7';

    @Test
    @DisplayName("HEX-код преобразуется в &x&R&R&G&G&B&B")
    void convertsHexCode() {

        String result = HexColorUtil.color("#42fffcText");

        assertEquals(SECTION + "x" + SECTION + "4" + SECTION + "2" + SECTION + "f"
                + SECTION + "f" + SECTION + "f" + SECTION + "c" + "Text", result);

    }

    @Test
    @DisplayName("Обычные цветовые коды & переводятся в секционный символ")
    void convertsLegacyCodes() {
        assertEquals(SECTION + "6Зарплата", HexColorUtil.color("&6Зарплата"));
    }

    @Test
    @DisplayName("Некорректный HEX остаётся без изменений")
    void keepsInvalidHex() {
        assertEquals("#12345", HexColorUtil.color("#12345"));
    }

    @Test
    @DisplayName("null и пустая строка возвращают пустую строку")
    void handlesEmptyInput() {

        assertEquals("", HexColorUtil.color(null));
        assertEquals("", HexColorUtil.color(""));

    }

    @Test
    @DisplayName("PlaceholderAPI не подключён - строка возвращается как есть")
    void placeholdersAreUntouchedWithoutApi() {

        assertFalse(PlaceholderUtil.isSupported());
        assertEquals("%server_online%", PlaceholderUtil.applyPlaceholders(null, "%server_online%"));

    }

    @Test
    @DisplayName("Плейсхолдеры %...% распознаются корректно")
    void detectsPlaceholders() {

        assertTrue(PlaceholderUtil.containsPlaceholders("%servertime_HH:mm%"));
        assertFalse(PlaceholderUtil.containsPlaceholders("50% скидки"));
        assertFalse(PlaceholderUtil.containsPlaceholders(null));

    }

    @Test
    @DisplayName("Целые суммы выводятся без дробной части")
    void formatsMoney() {

        assertEquals("500", PlaceholderUtil.formatMoney(500.0D));
        assertEquals("500.50", PlaceholderUtil.formatMoney(500.5D));

    }

    @Test
    @DisplayName("Длительность форматируется двумя старшими единицами")
    void formatsDuration() {

        assertEquals("0s", PlaceholderUtil.formatDuration(0L));
        assertEquals("5s", PlaceholderUtil.formatDuration(5_000L));
        assertEquals("1m 1s", PlaceholderUtil.formatDuration(61_000L));
        assertEquals("1m 15s", PlaceholderUtil.formatDuration(75_000L));
        assertEquals("1h", PlaceholderUtil.formatDuration(3_600_000L));
        assertEquals("1h 1m", PlaceholderUtil.formatDuration(3_660_000L));
        assertEquals("1d 1h", PlaceholderUtil.formatDuration(90_061_000L));

    }

    @Test
    @DisplayName("Встроенное время сервера подставляется без PlaceholderAPI")
    void appliesServerTime() {

        String result = PlaceholderUtil.applyServerTime("time: {servertime_HH:mm}");

        assertTrue(result.matches("time: \\d{2}:\\d{2}"));
        assertEquals("no tokens", PlaceholderUtil.applyServerTime("no tokens"));

    }

    @Test
    @DisplayName("Невалидный паттерн времени остаётся как есть")
    void keepsInvalidServerTimePattern() {

        String token = "{servertime_'}";

        assertEquals(token, PlaceholderUtil.applyServerTime(token));
        assertTrue(PlaceholderUtil.hasUnresolvedServerTime(token));
        assertFalse(PlaceholderUtil.hasUnresolvedServerTime(
                PlaceholderUtil.applyServerTime("{servertime_HH:mm} x")));

    }

    @Test
    @DisplayName("Внутренние плейсхолдеры подставляются в строку")
    void replacesTokens() {

        String result = PlaceholderUtil.replaceTokens("{group}: {money} / {interval} ({status}) {next}",
                null, "250", "staff", 3700L, "online", 2, "1h");

        assertEquals("staff: 250 / 3700 (online) 1h", result);

    }
}
