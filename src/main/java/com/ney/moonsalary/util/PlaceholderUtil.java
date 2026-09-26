package com.ney.moonsalary.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для работы с PlaceholderAPI и внутренними плейсхолдерами.
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

    /**
     * Плейсхолдер PlaceholderAPI: %идентификатор% без пробелов и вложенных '%'.
     * Точный поиск пары символов '%' давал бы ложные срабатывания на обычном
     * тексте вроде "скидка 10% + налог 5%".
     */
    private static final Pattern PAPI_PLACEHOLDER_PATTERN = Pattern.compile("%[^%\\s]+%");

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
     * @param text исходная строка
     * @return true если плейсхолдеры найдены
     */
    public static boolean containsPlaceholders(@Nullable String text) {
        return text != null && PAPI_PLACEHOLDER_PATTERN.matcher(text).find();
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
     * @param text          исходная строка
     * @param playerName    имя для {player} (игрок-контекст или получатель)
     * @param money         сумма выплаты
     * @param group         название группы
     * @param interval      интервал выплаты в секундах
     * @param status        статус игрока
     * @param commandsCount количество команд группы
     * @param next          обратный отсчёт до следующей выплаты
     * @param lastPayout    последняя выплата из истории (или статус "never")
     * @return строка с подставленными значениями
     */
    public static @NotNull String replaceTokens(@NotNull String text,
                                                @NotNull String playerName,
                                                @NotNull String money,
                                                @Nullable String group,
                                                long interval,
                                                @NotNull String status,
                                                int commandsCount,
                                                @NotNull String next,
                                                @NotNull String lastPayout) {
        return text
                .replace("{player}", playerName)
                .replace("{money}", money)
                .replace("{group}", group != null ? group : "none")
                .replace("{interval}", String.valueOf(interval))
                .replace("{status}", status)
                .replace("{commands}", String.valueOf(commandsCount))
                .replace("{next}", next)
                .replace("{last_payout}", lastPayout);
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

        String[] units = new String[4];
        int count = 0;
        count = appendUnit(units, count, days, "d");
        count = appendUnit(units, count, hours, "h");
        count = appendUnit(units, count, minutes, "m");
        count = appendUnit(units, count, seconds, "s");

        if (count == 0) {
            return "0s";
        }

        // оставляем две старшие единицы: "1d 3h", "2h 5m", "1m 1s"
        int kept = Math.min(count, 2);
        return String.join(" ", Arrays.copyOf(units, kept));

    }

    private static int appendUnit(@NotNull String[] units, int count,
                                  long value, @NotNull String unit) {

        if (value == 0L || count == units.length) {
            return count;
        }

        units[count] = value + unit;
        return count + 1;

    }

    /**
     * Форматирует сумму: целые значения без дробной части.
     * Дробные - через {@link Locale#ROOT}, чтобы десятичный разделитель
     * не зависел от локали сервера (запятая ломала бы команды с {money}).
     *
     * @param money сумма
     * @return строковое представление суммы
     */
    public static @NotNull String formatMoney(double money) {

        if (money == Math.rint(money) && !Double.isInfinite(money)) {
            return String.valueOf((long) money);
        }

        return String.format(Locale.ROOT, "%.2f", money);

    }

}
