package com.ney.moonsalary.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для работы с PlaceholderAPI.
 * <p>
 * PlaceholderAPI подключается через рефлексию, поэтому плагин
 * не требует его наличия на сервере (soft-зависимость).
 */
public class PlaceholderUtil {

    private static final @Nullable Class<?> PLACEHOLDER_API_CLASS;
    private static final @Nullable Method SET_PLACEHOLDERS_METHOD;

    static {

        Class<?> apiClass = null;
        Method setPlaceholdersMethod = null;

        try {

            apiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            setPlaceholdersMethod = apiClass.getMethod("setPlaceholders",
                    org.bukkit.OfflinePlayer.class, String.class);

        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // PlaceholderAPI не установлен - плейсхолдеры не обрабатываются
        }

        PLACEHOLDER_API_CLASS = apiClass;
        SET_PLACEHOLDERS_METHOD = setPlaceholdersMethod;

    }

    private static final Pattern SERVERTIME_PATTERN = Pattern.compile("\\{servertime_([^}]+)}");

    private PlaceholderUtil() {

    }

    /**
     * Подставляет встроенное время сервера: {servertime_<pattern>},
     * где pattern - формат Java DateTimeFormatter (HH:mm, dd/MM/yy и т.д.).
     * PlaceholderAPI и его экспаншены для этого не нужны.
     *
     * @param text исходная строка
     * @return строка с подставленным временем; невалидный токен остаётся как есть
     */
    public static @NotNull String applyServerTime(@NotNull String text) {

        if (!text.contains("{servertime_")) {
            return text;
        }

        Matcher matcher = SERVERTIME_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {

            String formatted = formatServerTime(matcher.group(1));

            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(formatted != null ? formatted : matcher.group()));

        }

        matcher.appendTail(result);
        return result.toString();

    }

    /**
     * Проверяет, остались ли неразобранные токены {servertime_...}
     * (признак невалидного паттерна).
     *
     * @param text строка после applyServerTime
     * @return true если токен не разобрался
     */
    public static boolean hasUnresolvedServerTime(@NotNull String text) {
        return text.contains("{servertime_");
    }

    private static @Nullable String formatServerTime(@NotNull String pattern) {

        try {
            return DateTimeFormatter.ofPattern(pattern).format(LocalDateTime.now());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Проверяет, доступен ли PlaceholderAPI на сервере.
     *
     * @return true если плагин найден и готов к работе
     */
    public static boolean isSupported() {
        return PLACEHOLDER_API_CLASS != null && SET_PLACEHOLDERS_METHOD != null;
    }

    /**
     * Проверяет, содержит ли строка плейсхолдеры формата %...%.
     *
     * @param text проверяемая строка
     * @return true если плейсхолдеры найдены
     */
    public static boolean containsPlaceholders(@Nullable String text) {
        return text != null && text.indexOf('%') != text.lastIndexOf('%');
    }

    /**
     * Обрабатывает плейсхолдеры PlaceholderAPI в строке.
     *
     * @param player игрок, от лица которого берутся плейсхолдеры
     * @param text   исходная строка
     * @return строка с подставленными плейсхолдерами
     */
    public static @NotNull String applyPlaceholders(@Nullable Player player,
                                                    @NotNull String text) {

        if (player == null || !isSupported()) {
            return text;
        }

        try {
            return (String) SET_PLACEHOLDERS_METHOD.invoke(null, player, text);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return text;
        }
    }

    /**
     * Подставляет внутренние плейсхолдеры плагина в строку.
     *
     * @param text        исходная строка
     * @param player      игрок (может быть null)
     * @param money       сумма выплаты
     * @param group       название группы
     * @param interval    интервал выплаты в секундах
     * @param status        статус игрока
     * @param commandsCount количество команд группы
     * @param next          обратный отсчёт до следующей выплаты
     * @return строка с подставленными значениями
     */
    public static @NotNull String replaceTokens(@NotNull String text,
                                                @Nullable Player player,
                                                @NotNull String money,
                                                @Nullable String group,
                                                long interval,
                                                @NotNull String status,
                                                int commandsCount,
                                                @NotNull String next) {

        return text
                .replace("{player}", player != null ? player.getName() : "unknown")
                .replace("{money}", money)
                .replace("{group}", group != null ? group : "none")
                .replace("{interval}", String.valueOf(interval))
                .replace("{status}", status)
                .replace("{commands}", String.valueOf(commandsCount))
                .replace("{next}", next);

    }

    /**
     * Форматирует длительность в компактный вид: две старшие ненулевые единицы.
     * Примеры: 3600000 -> "1h", 3660000 -> "1h 1m", 61000 -> "1m 1s", 5000 -> "5s".
     *
     * @param millis длительность в миллисекундах
     * @return человекочитаемая строка
     */
    public static @NotNull String formatDuration(long millis) {

        long totalSeconds = Math.max(0L, millis) / 1000L;

        long days = totalSeconds / 86400L;
        long hours = totalSeconds % 86400L / 3600L;
        long minutes = totalSeconds % 3600L / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder result = new StringBuilder();

        result = appendUnit(result, days, "d");
        result = appendUnit(result, hours, "h");
        result = appendUnit(result, minutes, "m");
        result = appendUnit(result, seconds, "s");

        if (result.length() == 0) {
            return "0s";
        }

        // оставляем две старшие единицы: "1d 3h", "2h 5m", "1m 1s", "1s  " -> trim
        String[] units = result.toString().trim().split("\s+");

        return units.length > 2 ? units[0] + " " + units[1] : String.join(" ", units);

    }

    private static @NotNull StringBuilder appendUnit(@NotNull StringBuilder builder,
                                                     long value,
                                                     @NotNull String unit) {

        if (value == 0L) {
            return builder;
        }

        if (builder.length() > 0) {
            builder.append(' ');
        }

        return builder.append(value).append(unit);

    }

    /**
     * Форматирует сумму: целые значения без дробной части.
     *
     * @param money сумма
     * @return отформатированная строка
     */
    public static @NotNull String formatMoney(double money) {

        if (money == Math.rint(money) && !Double.isInfinite(money)) {
            return String.valueOf((long) money);
        }

        return String.format("%.2f", money);

    }
}
