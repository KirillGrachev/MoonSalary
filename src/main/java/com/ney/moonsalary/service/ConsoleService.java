package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.util.HexColorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Вывод сообщений плагина в консоль.
 * <p>
 * Тексты берутся из секции messages.console конфигурации; пока конфигурация не загружена
 * (или ключ удалён) используется текст по умолчанию из {@link ConsoleMessage}.
 */
public class ConsoleService {

    private final MoonSalary plugin;
    private @Nullable ConfigManager configManager;

    public ConsoleService(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
    }

    /**
     * Подключает конфигурацию к сервису.
     * Вызывается сразу после создания ConfigManager.
     *
     * @param configManager менеджер конфигурации
     */
    public void attach(@NotNull ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Выводит сообщение в консоль.
     * <p>
     * Шаблоны могут использовать {prefix} и цветовые коды (& и HEX): они преобразуются
     * как в чате и передаются в лог - современные консоли отображают их самостоятельно.
     *
     * @param message тип сообщения
     * @param tokens  пары "имя плейсхолдера", "значение"
     */
    public void log(@NotNull ConsoleMessage message, @NotNull String... tokens) {

        if (configManager != null && !configManager.areConsoleMessagesEnabled()) {
            return;
        }

        String template = configManager != null
                ? configManager.getConsoleMessage(message.getKey(), message.getFallback())
                : message.getFallback();

        String formatted = HexColorUtil.color(
                applyTokens(template, tokens).replace("{prefix}", resolvePrefix()));

        plugin.getLogger().log(message.getLevel(), formatted);

    }

    private @NotNull String resolvePrefix() {

        if (configManager == null) {
            return "";
        }

        String prefix = configManager.getMessagePrefix();
        return prefix != null ? prefix : "";

    }

    /**
     * Подставляет пары плейсхолдеров {имя} в текст.
     *
     * @param text   исходный текст
     * @param tokens пары "имя", "значение"
     * @return текст с подставленными значениями
     */
    public static @NotNull String applyTokens(@NotNull String text, @NotNull String... tokens) {

        String result = text;

        for (int i = 0; i + 1 < tokens.length; i += 2) {
            result = result.replace("{" + tokens[i] + "}", tokens[i + 1]);
        }

        return result;

    }
}
