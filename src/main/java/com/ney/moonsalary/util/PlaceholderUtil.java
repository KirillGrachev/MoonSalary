package com.ney.moonsalary.util;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

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

    private PlaceholderUtil() {

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
     * @param status      статус игрока
     * @param commandsCount количество команд группы
     * @return строка с подставленными значениями
     */
    public static @NotNull String replaceTokens(@NotNull String text,
                                                @Nullable Player player,
                                                double money,
                                                @Nullable String group,
                                                long interval,
                                                @NotNull String status,
                                                int commandsCount) {

        return text
                .replace("{player}", player != null ? player.getName() : "unknown")
                .replace("{money}", formatMoney(money))
                .replace("{group}", group != null ? group : "none")
                .replace("{interval}", String.valueOf(interval))
                .replace("{status}", status)
                .replace("{commands}", String.valueOf(commandsCount));

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
