package com.ney.moonsalary.config.type;

import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Сообщения консоли плагина.
 * <p>
 * Каждый элемент хранит ключ секции messages.console, уровень логирования
 * и текст по умолчанию (используется, пока конфигурация не загружена
 * или если ключ удалён из config.yml).
 */
public enum ConsoleMessage {

    STARTUP("startup", Level.INFO,
            "MoonSalary успешно запущен! Групп: {groups}"),

    SHUTDOWN("shutdown", Level.INFO,
            "MoonSalary остановлен!"),

    DISABLED_BY_CONFIG("disabled_by_config", Level.WARNING,
            "Плагин выключен в config.yml (settings.enabled: false) - выплаты не производятся."),

    ECONOMY_HOOKED("economy_hooked", Level.INFO,
            "Экономика подключена: {provider}"),

    ECONOMY_WAITING("economy_waiting", Level.WARNING,
            "Экономика не найдена - повторная попытка после завершения запуска сервера."),

    ECONOMY_LATE("economy_late", Level.INFO,
            "Экономика найдена - MoonSalary полностью включён."),

    ECONOMY_MISSING("economy_missing", Level.SEVERE,
            "Плагин не включён: требуются Vault и плагин экономики (EssentialsX, CMI и т.д.). "
                    + "Установите зависимости и перезапустите сервер."),

    STARTUP_FAILED("startup_failed", Level.SEVERE,
            "MoonSalary не смог включиться: {reason} Выплаты и AFK-проверки отключены."),

    VAULT_PAUSED("vault_paused", Level.WARNING,
            "Vault выключен - выдача зарплат приостановлена."),

    VAULT_RESUMED("vault_resumed", Level.INFO,
            "Vault снова доступен - выдача зарплат возобновлена."),

    RELOADED("reloaded", Level.INFO,
            "Конфигурация перезагружена. Групп: {groups}"),

    INVALID_VALUE("invalid_value", Level.WARNING,
            "Некорректное значение '{path}': {value}. Используется: {defaultValue}."),

    GROUP_NO_SALARY("group_no_salary", Level.WARNING,
            "Группа '{group}' не имеет поля 'salary' - пропущена."),

    GROUPS_SECTION_MISSING("groups_section_missing", Level.WARNING,
            "Секция 'groups' не найдена - зарплаты выдаваться не будут."),

    GROUPS_EMPTY("groups_empty", Level.WARNING,
            "Не найдено ни одной группы зарплат."),

    UNKNOWN_SOUND("unknown_sound", Level.WARNING,
            "Неизвестный звук в '{path}'."),

    COMMAND_MISSING("command_missing", Level.WARNING,
            "Команда '{command}' не найдена в plugin.yml."),

    PLACEHOLDERS_NO_API("placeholders_no_api", Level.WARNING,
            "В сообщениях используются плейсхолдеры %...%, "
                    + "но PlaceholderAPI не установлен - они не будут обработаны."),

    DEPOSIT_FAILED("deposit_failed", Level.WARNING,
            "Не удалось выдать {money} игроку {player}: {reason}"),

    DEPOSIT_EXCEPTION("deposit_exception", Level.WARNING,
            "Ошибка экономики при выдаче зарплаты игроку {player}: {reason}");

    private final String key;
    private final Level level;
    private final String fallback;

    ConsoleMessage(@NotNull String key, @NotNull Level level, @NotNull String fallback) {

        this.key = key;
        this.level = level;
        this.fallback = fallback;

    }

    public @NotNull String getKey() {
        return key;
    }

    public @NotNull Level getLevel() {
        return level;
    }

    public @NotNull String getFallback() {
        return fallback;
    }
}
