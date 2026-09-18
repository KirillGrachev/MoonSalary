package com.ney.moonsalary.util;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для обработки HEX-цветов в строках.
 * Конвертирует формат #RRGGBB в &x&R&R&G&G&B&B.
 */
public class HexColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    private HexColorUtil() {

    }

    /**
     * Преобразует HEX-коды в строке в Minecraft-формат.
     *
     * @param text исходная строка с HEX-кодами
     * @return строка с преобразованными цветовыми кодами
     */
    public static @NotNull String color(@Nullable String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(text);

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {

            String hexCode = matcher.group();

            if (hexCode.length() != 7 || !isValidHexCode(hexCode)) {
                continue;
            }

            result.append(text, lastEnd, matcher.start());

            // Преобразуем #RRGGBB -> &x&R&R&G&G&B&B
            StringBuilder replacement = new StringBuilder("&x");
            for (int i = 1; i < hexCode.length(); i++) {
                replacement.append("&").append(hexCode.charAt(i));
            }

            result.append(replacement);
            lastEnd = matcher.end();

        }

        result.append(text.substring(lastEnd));
        return ChatColor.translateAlternateColorCodes('&',
                result.toString());

    }

    /**
     * Проверяет валидность HEX-кода.
     *
     * @param code строка с HEX-кодом (#RRGGBB)
     * @return true если код валиден, false иначе
     */
    private static boolean isValidHexCode(@NotNull String code) {

        for (int i = 1; i < code.length(); i++) {

            char c = code.charAt(i);
            if (!Character.isDigit(c) && (c < 'a' || c > 'f')
                    && (c < 'A' || c > 'F')) {
                return false;
            }

        }

        return true;

    }
}
