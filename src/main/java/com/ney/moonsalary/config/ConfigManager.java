package com.ney.moonsalary.config;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.config.type.SqlSettings;
import com.ney.moonsalary.config.type.StorageSettings;
import com.ney.moonsalary.config.type.StorageType;
import com.ney.moonsalary.service.ConsoleService;
import com.ney.moonsalary.util.HexColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Конфигурация плагина из двух файлов:
 * <ul>
 *     <li>config.yml - поведение и все тексты для игроков (settings, messages);</li>
 *     <li>groups.yml - группы зарплат (salary, priority, messages, commands).</li>
 * </ul>
 * Стратегия кэширования единая: все значения, включая шаблоны сообщений,
 * читаются из YAML один раз на загрузке/reload и отдаются геттерами как есть -
 * ничего не перечитывается и не перекрашивается на каждый вызов.
 * Списки и карты возвращаются неизменяемыми.
 * <p>
 * Тексты консоли в конфиге не хранятся: они живут в {@link ConsoleMessage},
 * чтобы пользователь не мог сломать диагностику правкой файла.
 * <p>
 * Все вызовы происходят из основного потока сервера.
 */
public class ConfigManager implements MoonSalaryConfig {

    private final MoonSalary plugin;
    private final ConsoleService consoleService;

    private FileConfiguration config;
    private FileConfiguration groupsConfig;

    private static final String FILE_GROUPS = "groups.yml";

    private static final String PATH_ENABLED = "settings.enabled";
    private static final String PATH_PAYOUT_MODE = "settings.payout.mode";
    private static final String PATH_FALLBACK_GROUP = "settings.payout.fallback_group";
    private static final String PATH_INTERVAL = "settings.payout.interval";
    private static final String PATH_PAUSE_WHILE_AFK = "settings.payout.pause_while_afk";
    private static final String PATH_EXECUTE_COMMANDS = "settings.execute_commands";
    private static final String PATH_FORMAT_MONEY = "settings.format_money";
    private static final String PATH_TITLE_ENABLED = "settings.title.enabled";
    private static final String PATH_TITLE_FADE_IN = "settings.title.fade_in";
    private static final String PATH_TITLE_STAY = "settings.title.stay";
    private static final String PATH_TITLE_FADE_OUT = "settings.title.fade_out";
    private static final String PATH_AFK_ENABLED = "settings.afk.enabled";
    private static final String PATH_AFK_CHECK_INTERVAL = "settings.afk.check_interval";
    private static final String PATH_AFK_THRESHOLD = "settings.afk.threshold";
    private static final String PATH_AFK_IGNORE_ROTATION = "settings.afk.ignore_rotation";
    private static final String PATH_PERMISSIONS_ENABLED = "settings.permissions.enabled";
    private static final String PATH_PERMISSION_PREFIX = "settings.permissions.prefix";
    private static final String PATH_PERMISSION_BYPASS_AFK = "settings.permissions.bypass_afk";
    private static final String PATH_PERMISSION_RELOAD = "settings.permissions.reload";
    private static final String PATH_PERMISSION_LIST = "settings.permissions.list";
    private static final String PATH_PERMISSION_INFO = "settings.permissions.info";
    private static final String PATH_STORAGE_TYPE = "settings.storage.type";
    private static final String PATH_STORAGE_DOWNLOAD = "settings.storage.download_libraries";
    private static final String PATH_STORAGE_HISTORY_LIMIT = "settings.storage.history_limit";
    private static final String PATH_STORAGE_SQL = "settings.storage.sql";
    private static final String PATH_SOUND_SALARY_ENABLED = "settings.sounds.salary.enabled";
    private static final String PATH_SOUND_SALARY_NAME = "settings.sounds.salary.sound";
    private static final String PATH_SOUND_SALARY_VOLUME = "settings.sounds.salary.volume";
    private static final String PATH_SOUND_SALARY_PITCH = "settings.sounds.salary.pitch";
    private static final String PATH_GROUPS = "groups";
    private static final String PATH_PERMISSION_GIVE = "settings.permissions.give";
    private static final String PATH_MESSAGE_PREFIX = "messages.prefix";
    private static final String PATH_MESSAGES_ENABLED = "messages.on_salary.enabled";
    private static final String PATH_SALARY_TITLE = "messages.on_salary.title";
    private static final String PATH_SALARY_SUBTITLE = "messages.on_salary.subtitle";
    private static final String PATH_BLOCKED_AFK = "messages.on_salary.blocked_afk";
    private static final String PATH_NO_PERMISSION = "messages.command.no_permission";
    private static final String PATH_USAGE = "messages.command.usage";
    private static final String PATH_UNKNOWN_PLAYER = "messages.command.unknown_player";
    private static final String PATH_RELOAD_SUCCESS = "messages.command.reload_success";
    private static final String PATH_GIVE_USAGE = "messages.command.give.usage";
    private static final String PATH_GIVE_SUCCESS = "messages.command.give.success";
    private static final String PATH_GIVE_FAILED = "messages.command.give.failed";
    private static final String PATH_GIVE_UNKNOWN_GROUP = "messages.command.give.unknown_group";
    private static final String PATH_INFO_SELF = "messages.command.info.self";
    private static final String PATH_INFO_OTHER = "messages.command.info.other";
    private static final String PATH_LIST_HEADER = "messages.command.list.header";
    private static final String PATH_LIST_ENTRY = "messages.command.list.entry";
    private static final String PATH_LIST_EMPTY = "messages.command.list.empty";
    private static final String PATH_STATUS_ONLINE = "messages.command.status.online";
    private static final String PATH_STATUS_AFK = "messages.command.status.afk";
    private static final String PATH_STATUS_NO_GROUP = "messages.command.status.no_group";
    private static final String PATH_STATUS_NO_PAYOUT = "messages.command.status.no_payout";
    private static final long MIN_INTERVAL_SECONDS = 1L;
    private static final long TICKS_PER_SECOND = 20L;
    private static final long MILLIS_PER_SECOND = 1000L;

    private boolean enabled;
    private boolean commandsEnabled;
    private boolean moneyFormattingEnabled;
    private boolean titleEnabled;
    private int titleFadeIn;
    private int titleStay;
    private int titleFadeOut;
    private boolean afkEnabled;
    private long afkCheckIntervalTicks;
    private long afkThresholdMillis;
    private boolean afkRotationIgnored;
    private boolean permissionsEnabled;
    private String groupPermissionPrefix;
    private String permissionBypassAfk;
    private String permissionReload;
    private String permissionList;
    private String permissionInfo;
    private String permissionGive;
    private StorageSettings storageSettings;
    private boolean downloadLibrariesEnabled;
    private SoundSettings salarySound;
    private PayoutMode payoutMode;
    private boolean pauseWhileAfk;
    private String fallbackGroup;
    private long salaryIntervalSeconds;
    private String messagePrefix;
    private boolean messagesEnabled;
    private String salaryTitle;
    private String salarySubtitle;
    private String blockedAfkMessage;
    private List<String> infoSelfMessage;
    private List<String> infoOtherMessage;
    private String listHeader;
    private String listEntry;
    private String listEmpty;
    private String statusOnline;
    private String statusAfk;
    private String statusNoGroup;
    private String statusNoPayout;
    private String noPermissionMessage;
    private String usageMessage;
    private String unknownPlayerMessage;
    private String reloadSuccessMessage;
    private String giveUsageMessage;
    private String giveSuccessMessage;
    private String giveFailedMessage;
    private String giveUnknownGroupMessage;
    private List<SalaryGroupSettings> groups;

    public ConfigManager(@NotNull MoonSalary plugin, @NotNull ConsoleService consoleService) {

        this.plugin = plugin;
        this.consoleService = consoleService;

        plugin.saveDefaultConfig();
        saveResourceIfMissing(FILE_GROUPS);
        loadConfigs();
        cacheConfigValues();

    }

    private void saveResourceIfMissing(@NotNull String resourceName) {

        File target = new File(plugin.getDataFolder(), resourceName);

        if (!target.exists()) {
            plugin.saveResource(resourceName, false);
        }

    }

    private void loadConfigs() {
        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        groupsConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), FILE_GROUPS));
    }

    private void cacheConfigValues() {

        enabled = config.getBoolean(PATH_ENABLED, true);
        commandsEnabled = config.getBoolean(PATH_EXECUTE_COMMANDS, true);
        moneyFormattingEnabled = config.getBoolean(PATH_FORMAT_MONEY, false);
        titleEnabled = config.getBoolean(PATH_TITLE_ENABLED, true);
        titleFadeIn = config.getInt(PATH_TITLE_FADE_IN, 20);
        titleStay = config.getInt(PATH_TITLE_STAY, 70);
        titleFadeOut = config.getInt(PATH_TITLE_FADE_OUT, 20);
        afkEnabled = config.getBoolean(PATH_AFK_ENABLED, true);
        afkRotationIgnored = config.getBoolean(PATH_AFK_IGNORE_ROTATION, true);
        afkCheckIntervalTicks = secondsToTicks(
                config.getInt(PATH_AFK_CHECK_INTERVAL, 20), "settings.afk.check_interval");
        afkThresholdMillis = secondsToMillis(
                config.getInt(PATH_AFK_THRESHOLD, 300), "settings.afk.threshold");
        permissionsEnabled = config.getBoolean(PATH_PERMISSIONS_ENABLED, true);
        groupPermissionPrefix = config.getString(PATH_PERMISSION_PREFIX, "group.");
        permissionBypassAfk = config.getString(PATH_PERMISSION_BYPASS_AFK, "moonsalary.bypass.afk");
        permissionReload = config.getString(PATH_PERMISSION_RELOAD, "moonsalary.admin.reload");
        permissionList = config.getString(PATH_PERMISSION_LIST, "moonsalary.admin.list");
        permissionInfo = config.getString(PATH_PERMISSION_INFO, "moonsalary.admin.info");
        permissionGive = config.getString(PATH_PERMISSION_GIVE, "moonsalary.admin.give");
        storageSettings = loadStorageSettings();
        downloadLibrariesEnabled = config.getBoolean(PATH_STORAGE_DOWNLOAD, true);
        salarySound = loadSound("settings.sounds.salary",
                PATH_SOUND_SALARY_ENABLED, PATH_SOUND_SALARY_NAME,
                PATH_SOUND_SALARY_VOLUME, PATH_SOUND_SALARY_PITCH);
        payoutMode = parsePayoutMode();
        pauseWhileAfk = config.getBoolean(PATH_PAUSE_WHILE_AFK, true);
        fallbackGroup = config.getString(PATH_FALLBACK_GROUP, "default");
        salaryIntervalSeconds = secondsOrWarn(
                config.getInt(PATH_INTERVAL, 3600), PATH_INTERVAL);
        messagePrefix = color(config.getString(PATH_MESSAGE_PREFIX, ""));
        messagesEnabled = config.getBoolean(PATH_MESSAGES_ENABLED, true);
        salaryTitle = color(config.getString(PATH_SALARY_TITLE, ""));
        salarySubtitle = color(config.getString(PATH_SALARY_SUBTITLE, ""));
        blockedAfkMessage = color(config.getString(PATH_BLOCKED_AFK, ""));
        infoSelfMessage = colorList(config.getStringList(PATH_INFO_SELF));
        infoOtherMessage = colorList(config.getStringList(PATH_INFO_OTHER));
        listHeader = color(config.getString(PATH_LIST_HEADER, ""));
        listEntry = color(config.getString(PATH_LIST_ENTRY, ""));
        listEmpty = color(config.getString(PATH_LIST_EMPTY, ""));
        statusOnline = color(config.getString(PATH_STATUS_ONLINE, "&aonline"));
        statusAfk = color(config.getString(PATH_STATUS_AFK, "&cAFK"));
        statusNoGroup = color(config.getString(PATH_STATUS_NO_GROUP, "&cgroup not found"));
        statusNoPayout = color(config.getString(PATH_STATUS_NO_PAYOUT, "&8never"));
        noPermissionMessage = color(config.getString(PATH_NO_PERMISSION, ""));
        usageMessage = color(config.getString(PATH_USAGE, ""));
        unknownPlayerMessage = color(config.getString(PATH_UNKNOWN_PLAYER, ""));
        reloadSuccessMessage = color(config.getString(PATH_RELOAD_SUCCESS, ""));
        giveUsageMessage = color(config.getString(PATH_GIVE_USAGE, ""));
        giveSuccessMessage = color(config.getString(PATH_GIVE_SUCCESS, ""));
        giveFailedMessage = color(config.getString(PATH_GIVE_FAILED, ""));
        giveUnknownGroupMessage = color(config.getString(PATH_GIVE_UNKNOWN_GROUP, ""));
        groups = loadGroups();
        validateFallbackGroup();

    }

    /**
     * Читает настройки хранилища: тип, лимит истории и подключение SQL.
     *
     * @return неизменяемая связка настроек
     */
    private @NotNull StorageSettings loadStorageSettings() {

        String rawType = config.getString(PATH_STORAGE_TYPE, StorageType.YAML.name());
        StorageType type = StorageType.of(rawType, StorageType.YAML);

        if (!type.name().equalsIgnoreCase(rawType != null ? rawType.trim() : "")) {
            consoleService.log(ConsoleMessage.INVALID_VALUE,
                    "path", PATH_STORAGE_TYPE,
                    "value", String.valueOf(rawType),
                    "defaultValue", StorageType.YAML.name());
        }

        int historyLimit = config.getInt(PATH_STORAGE_HISTORY_LIMIT, 10);

        if (historyLimit < 0) {
            consoleService.log(ConsoleMessage.INVALID_VALUE,
                    "path", PATH_STORAGE_HISTORY_LIMIT,
                    "value", String.valueOf(historyLimit),
                    "defaultValue", "10");
            historyLimit = 10;
        }

        ConfigurationSection sqlSection = config.getConfigurationSection(PATH_STORAGE_SQL);
        Map<String, String> properties = new HashMap<>();

        if (sqlSection != null) {

            ConfigurationSection propertiesSection = sqlSection.getConfigurationSection("properties");

            if (propertiesSection != null) {
                for (String key : propertiesSection.getKeys(false)) {
                    properties.put(key, propertiesSection.getString(key, ""));
                }
            }

        }

        SqlSettings sql = new SqlSettings(
                sqlSection != null ? sqlSection.getString("host", "localhost") : "localhost",
                sqlSection != null ? sqlSection.getInt("port", 3306) : 3306,
                sqlSection != null ? sqlSection.getString("database", "moonsalary") : "moonsalary",
                sqlSection != null ? sqlSection.getString("table", "moonsalary") : "moonsalary",
                sqlSection != null ? sqlSection.getString("user", "root") : "root",
                sqlSection != null ? sqlSection.getString("password", "") : "",
                Collections.unmodifiableMap(properties)
        );

        return new StorageSettings(type, sql, historyLimit);

    }

    /**
     * Проверяет, что fallback-группа действительно настроена в секции groups.
     */
    private void validateFallbackGroup() {

        if (fallbackGroup == null || fallbackGroup.isEmpty()) {
            return;
        }

        boolean exists = groups.stream()
                .anyMatch(settings -> settings.name().equalsIgnoreCase(fallbackGroup));

        if (!exists) {
            consoleService.log(ConsoleMessage.FALLBACK_GROUP_MISSING,
                    "group", fallbackGroup);
        }

    }

    /**
     * Загружает список групп зарплат из секции groups файла groups.yml.
     *
     * @return неизменяемый список настроек групп
     */
    private @NotNull List<SalaryGroupSettings> loadGroups() {

        ConfigurationSection section = groupsConfig.getConfigurationSection(PATH_GROUPS);

        if (section == null) {
            consoleService.log(ConsoleMessage.GROUPS_SECTION_MISSING);
            return Collections.emptyList();
        }

        Set<String> keys = section.getKeys(false);
        List<SalaryGroupSettings> loadedGroups = new ArrayList<>(keys.size());

        for (String groupName : keys) {

            ConfigurationSection groupSection = section.getConfigurationSection(groupName);
            if (groupSection == null) continue;

            if (!groupSection.isSet("salary")) {
                consoleService.log(ConsoleMessage.GROUP_NO_SALARY, "group", groupName);
                continue;
            }

            loadedGroups.add(new SalaryGroupSettings(
                    groupName,
                    groupSection.getDouble("salary"),
                    groupSection.getInt("priority"),
                    colorList(groupSection.getStringList("messages")),
                    loadCommands(groupSection)
            ));

        }

        if (loadedGroups.isEmpty()) {
            consoleService.log(ConsoleMessage.GROUPS_EMPTY);
        }

        return Collections.unmodifiableList(loadedGroups);

    }

    /**
     * Загружает команды группы (поддерживаются и список, и одиночная строка).
     *
     * @param groupSection секция группы
     * @return список команд без пустых значений
     */
    private @NotNull List<String> loadCommands(@NotNull ConfigurationSection groupSection) {
        List<String> commands = groupSection.isList("commands")
                ? groupSection.getStringList("commands")
                : Collections.singletonList(groupSection.getString("commands", ""));
        return commands.stream()
                .filter(command -> command != null && !command.isBlank())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Читает режим выплат, при некорректном значении возвращается GLOBAL.
     *
     * @return режим выплат
     */
    private @NotNull PayoutMode parsePayoutMode() {

        String configValue = config.getString(PATH_PAYOUT_MODE, "GLOBAL");
        try {
            return PayoutMode.valueOf(configValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            consoleService.log(ConsoleMessage.INVALID_VALUE,
                    "path", PATH_PAYOUT_MODE,
                    "value", configValue,
                    "defaultValue", PayoutMode.GLOBAL.name());
            return PayoutMode.GLOBAL;
        }

    }

    private @NotNull SoundSettings loadSound(@NotNull String logPath,
                                             @NotNull String enabledPath,
                                             @NotNull String namePath,
                                             @NotNull String volumePath,
                                             @NotNull String pitchPath) {
        return SoundSettings.of(
                config.getString(namePath),
                config.getBoolean(enabledPath, true),
                (float) config.getDouble(volumePath, 1.0D),
                (float) config.getDouble(pitchPath, 1.0D),
                () -> consoleService.log(ConsoleMessage.UNKNOWN_SOUND, "path", logPath + ".sound")
        );
    }

    private long secondsToTicks(int seconds, @NotNull String path) {
        return secondsOrWarn(seconds, path) * TICKS_PER_SECOND;
    }

    private long secondsToMillis(int seconds, @NotNull String path) {
        return secondsOrWarn(seconds, path) * MILLIS_PER_SECOND;
    }

    private long secondsOrWarn(int seconds, @NotNull String path) {

        if (seconds < MIN_INTERVAL_SECONDS) {
            consoleService.log(ConsoleMessage.INVALID_VALUE,
                    "path", path,
                    "value", String.valueOf(seconds),
                    "defaultValue", String.valueOf(MIN_INTERVAL_SECONDS));
            return MIN_INTERVAL_SECONDS;
        }

        return seconds;

    }

    private @NotNull String color(String text) {
        return HexColorUtil.color(text);
    }

    private @NotNull List<String> colorList(@NotNull List<String> list) {
        return list.stream()
                .map(HexColorUtil::color)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean areCommandsEnabled() {
        return commandsEnabled;
    }

    @Override
    public boolean isMoneyFormattingEnabled() {
        return moneyFormattingEnabled;
    }

    @Override
    public boolean isTitleEnabled() {
        return titleEnabled;
    }

    @Override
    public boolean isAfkEnabled() {
        return afkEnabled;
    }

    @Override
    public boolean isAfkRotationIgnored() {
        return afkRotationIgnored;
    }

    @Override
    public boolean areMessagesEnabled() {
        return messagesEnabled;
    }

    @Override
    public boolean arePermissionsEnabled() {
        return permissionsEnabled;
    }

    @Override
    public @NotNull PayoutMode getPayoutMode() {
        return payoutMode;
    }

    @Override
    public boolean isPayoutPausedWhileAfk() {
        return pauseWhileAfk;
    }

    @Override
    public @NotNull String getFallbackGroup() {
        return fallbackGroup != null ? fallbackGroup : "";
    }

    @Override
    public long getSalaryIntervalTicks() {
        return salaryIntervalSeconds * TICKS_PER_SECOND;
    }

    @Override
    public long getSalaryIntervalMillis() {
        return salaryIntervalSeconds * MILLIS_PER_SECOND;
    }

    @Override
    public long getSalaryIntervalSeconds() {
        return salaryIntervalSeconds;
    }

    @Override
    public long getAfkCheckIntervalTicks() {
        return afkCheckIntervalTicks;
    }

    @Override
    public long getAfkThresholdMillis() {
        return afkThresholdMillis;
    }

    @Override
    public int getTitleFadeIn() {
        return titleFadeIn;
    }

    @Override
    public int getTitleStay() {
        return titleStay;
    }

    @Override
    public int getTitleFadeOut() {
        return titleFadeOut;
    }

    @Override
    public @NotNull SoundSettings getSalarySound() {
        return salarySound;
    }

    @Override
    public @NotNull String getGroupPermissionPrefix() {
        return groupPermissionPrefix;
    }

    @Override
    public @NotNull String getPermissionBypassAfk() {
        return permissionBypassAfk;
    }

    @Override
    public @NotNull String getPermissionReload() {
        return permissionReload;
    }

    @Override
    public @NotNull String getPermissionList() {
        return permissionList;
    }

    @Override
    public @NotNull String getPermissionInfo() {
        return permissionInfo;
    }

    @Override
    public @NotNull String getPermissionGive() {
        return permissionGive;
    }

    @Override
    public @NotNull StorageSettings getStorageSettings() {
        return storageSettings;
    }

    @Override
    public boolean isDownloadLibrariesEnabled() {
        return downloadLibrariesEnabled;
    }

    @Override
    public int getHistoryLimit() {
        return storageSettings.historyLimit();
    }

    @Override
    public @NotNull String getMessagePrefix() {
        return messagePrefix;
    }

    @Override
    public @NotNull String getSalaryTitle() {
        return salaryTitle;
    }

    @Override
    public @NotNull String getSalarySubtitle() {
        return salarySubtitle;
    }

    @Override
    public @NotNull String getBlockedAfkMessage() {
        return blockedAfkMessage;
    }

    @Override
    public @NotNull List<String> getInfoSelfMessage() {
        return infoSelfMessage;
    }

    @Override
    public @NotNull List<String> getInfoOtherMessage() {
        return infoOtherMessage;
    }

    @Override
    public @NotNull String getListHeader() {
        return listHeader;
    }

    @Override
    public @NotNull String getListEntry() {
        return listEntry;
    }

    @Override
    public @NotNull String getListEmpty() {
        return listEmpty;
    }

    @Override
    public @NotNull String getStatusOnline() {
        return statusOnline;
    }

    @Override
    public @NotNull String getStatusAfk() {
        return statusAfk;
    }

    @Override
    public @NotNull String getStatusNoGroup() {
        return statusNoGroup;
    }

    @Override
    public @NotNull String getStatusNoPayout() {
        return statusNoPayout;
    }

    @Override
    public @NotNull String getNoPermissionMessage() {
        return noPermissionMessage;
    }

    @Override
    public @NotNull String getUsageMessage() {
        return usageMessage;
    }

    @Override
    public @NotNull String getUnknownPlayerMessage() {
        return unknownPlayerMessage;
    }

    @Override
    public @NotNull String getReloadSuccessMessage() {
        return reloadSuccessMessage;
    }

    @Override
    public @NotNull String getGiveUsageMessage() {
        return giveUsageMessage;
    }

    @Override
    public @NotNull String getGiveSuccessMessage() {
        return giveSuccessMessage;
    }

    @Override
    public @NotNull String getGiveFailedMessage() {
        return giveFailedMessage;
    }

    @Override
    public @NotNull String getGiveUnknownGroupMessage() {
        return giveUnknownGroupMessage;
    }

    @Override
    public @NotNull List<SalaryGroupSettings> getGroups() {
        return groups;
    }

    /**
     * Перечитывает оба файла конфигурации и перекэширует значения.
     */
    public void reload() {
        loadConfigs();
        cacheConfigValues();
    }

}
