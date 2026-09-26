package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.util.HexColorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Вывод сообщений плагина в консоль.
 * <p>
 * Тексты живут в {@link ConsoleMessage} (код), а не в конфиге: служебную
 * диагностику пользователь не может сломать правкой файла. Плейсхолдеры {}
 * и &-цвета поддерживаются в самих текстах сообщений.
 */
public class ConsoleService {

    private final MoonSalary plugin;

    public ConsoleService(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
    }

    /**
     * Выводит сообщение в консоль.
     *
     * @param message тип сообщения
     * @param tokens  пары "имя плейсхолдера", "значение"
     */
    public void log(@NotNull ConsoleMessage message, @NotNull String... tokens) {
        plugin.getLogger().log(message.getLevel(),
                HexColorUtil.color(applyTokens(message.getText(), tokens)));
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
