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
            "MoonSalary is up and running! Groups: {groups}"),

    SHUTDOWN("shutdown", Level.INFO,
            "MoonSalary stopped!"),

    DISABLED_BY_CONFIG("disabled_by_config", Level.WARNING,
            "The plugin is disabled in config.yml (settings.enabled: false) - no payouts will be made."),

    ECONOMY_HOOKED("economy_hooked", Level.INFO,
            "Economy hooked: {provider}"),

    ECONOMY_WAITING("economy_waiting", Level.WARNING,
            "Economy not found - retrying after the server startup completes."),

    ECONOMY_LATE("economy_late", Level.INFO,
            "Economy found - MoonSalary is fully enabled."),

    ECONOMY_MISSING("economy_missing", Level.SEVERE,
            "MoonSalary could not enable: Vault and an economy plugin (EssentialsX, CMI, etc.) are required. "
                    + "Install the dependencies and restart the server."),

    STARTUP_FAILED("startup_failed", Level.SEVERE,
            "MoonSalary could not start: {reason} Payouts and AFK checks are disabled."),

    VAULT_PAUSED("vault_paused", Level.WARNING,
            "Vault was disabled - salary payouts are paused."),

    VAULT_RESUMED("vault_resumed", Level.INFO,
            "Vault is back - salary payouts are resumed."),

    RELOADED("reloaded", Level.INFO,
            "Configuration reloaded. Groups: {groups}"),

    INVALID_VALUE("invalid_value", Level.WARNING,
            "Invalid value for '{path}': {value}. Using: {defaultValue}."),

    GROUP_NO_SALARY("group_no_salary", Level.WARNING,
            "Group '{group}' has no 'salary' field - skipped."),

    GROUPS_SECTION_MISSING("groups_section_missing", Level.WARNING,
            "Section 'groups' not found - no salaries will be paid."),

    GROUPS_EMPTY("groups_empty", Level.WARNING,
            "No salary groups found."),

    FALLBACK_GROUP_MISSING("fallback_group_missing", Level.WARNING,
            "Fallback group '{group}' not found in groups - fallback is disabled."),

    UNKNOWN_SOUND("unknown_sound", Level.WARNING,
            "Unknown sound at '{path}'."),

    COMMAND_MISSING("command_missing", Level.WARNING,
            "Command '{command}' not found in plugin.yml."),

    COMMAND_UNREGISTER_FAILED("command_unregister_failed", Level.WARNING,
            "Failed to unregister command '{command}': {reason}"),

    INVALID_TIME_PATTERN("invalid_time_pattern", Level.WARNING,
            "Invalid servertime pattern in messages - token left unchanged: {token}"),

    PLACEHOLDERS_NO_API("placeholders_no_api", Level.WARNING,
            "Messages contain %...% placeholders "
                    + "but PlaceholderAPI is not installed - they will not be processed."),

    DEPOSIT_FAILED("deposit_failed", Level.WARNING,
            "Failed to deposit {money} to {player}: {reason}"),

    DEPOSIT_EXCEPTION("deposit_exception", Level.WARNING,
            "Economy error while paying {player}: {reason}");

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
