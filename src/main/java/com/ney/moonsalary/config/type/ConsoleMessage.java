package com.ney.moonsalary.config.type;

import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Сообщения консоли плагина.
 * <p>
 * Тексты живут только в коде: это служебный канал диагностики, и пользователь
 * не может случайно сломать его правкой конфига. У каждого сообщения свой
 * уровень логирования и текст с плейсхолдерами в {}.
 */
public enum ConsoleMessage {

    STARTUP(Level.INFO,
            "MoonSalary is up and running! Groups: {groups}"),

    SHUTDOWN(Level.INFO,
            "MoonSalary stopped!"),

    DISABLED_BY_CONFIG(Level.WARNING,
            "The plugin is disabled in config.yml (settings.enabled: false) - no payouts will be made."),

    ECONOMY_HOOKED(Level.INFO,
            "Economy hooked: {provider}"),

    ECONOMY_WAITING(Level.WARNING,
            "Economy not found - retrying after the server startup completes."),

    ECONOMY_LATE(Level.INFO,
            "Economy found - MoonSalary is fully enabled."),

    ECONOMY_MISSING(Level.SEVERE,
            "MoonSalary could not enable: Vault and an economy plugin (EssentialsX, CMI, etc.) are required. "
                    + "Install the dependencies and restart the server."),

    ECONOMY_PROVIDER_LOST(Level.WARNING,
            "Economy provider was unregistered - salary payouts are paused until a provider is back."),

    STARTUP_FAILED(Level.SEVERE,
            "MoonSalary could not start: {reason} Payouts and AFK checks are disabled."),

    VAULT_PAUSED(Level.WARNING,
            "Vault was disabled - salary payouts are paused."),

    VAULT_RESUMED(Level.INFO,
            "Vault is back - salary payouts are resumed."),

    RELOADED(Level.INFO,
            "Configuration reloaded. Groups: {groups}"),

    INVALID_VALUE(Level.WARNING,
            "Invalid value for '{path}': {value}. Using: {defaultValue}."),

    GROUP_NO_SALARY(Level.WARNING,
            "Group '{group}' has no 'salary' field - skipped."),

    GROUPS_SECTION_MISSING(Level.WARNING,
            "Section 'groups' not found - no salaries will be paid."),

    GROUPS_EMPTY(Level.WARNING,
            "No salary groups found."),

    FALLBACK_GROUP_MISSING(Level.WARNING,
            "Fallback group '{group}' not found in groups - fallback is disabled."),

    UNKNOWN_SOUND(Level.WARNING,
            "Unknown sound at '{path}'."),

    COMMAND_MISSING(Level.WARNING,
            "Command '{command}' not found in plugin.yml."),

    COMMAND_UNREGISTER_FAILED(Level.WARNING,
            "Failed to unregister command '{command}': {reason}"),

    INVALID_TIME_PATTERN(Level.WARNING,
            "Invalid servertime pattern in messages - token left unchanged: {token}"),

    PLACEHOLDERS_NO_API(Level.WARNING,
            "Messages contain %...% placeholders "
                    + "but PlaceholderAPI is not installed - they will not be processed."),

    DEPOSIT_FAILED(Level.WARNING,
            "Failed to deposit {money} to {player}: {reason}"),

    DEPOSIT_EXCEPTION(Level.WARNING,
            "Economy error while paying {player}: {reason}"),

    PAYOUT_ERROR(Level.WARNING,
            "Salary payout for {player} failed: {reason}"),

    COMMAND_ERROR(Level.WARNING,
            "Command '{command}' while paying {player} failed: {reason}");

    private final Level level;
    private final String text;

    ConsoleMessage(@NotNull Level level, @NotNull String text) {
        this.level = level;
        this.text = text;
    }

    public @NotNull Level getLevel() {
        return level;
    }

    public @NotNull String getText() {
        return text;
    }

}
