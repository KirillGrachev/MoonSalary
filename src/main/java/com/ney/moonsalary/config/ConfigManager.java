package com.ney.moonsalary.config;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.AfkNotifyType;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.util.HexColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager implements MoonSalaryConfig {

    private final MoonSalary plugin;
    private FileConfiguration config;

    private static final String PATH_ENABLED = "settings.enabled";
    private static final String PATH_INTERVAL = "settings.interval";
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
    private static final String PATH_AFK_NOTIFY_ENABLED = "settings.afk.notify.enabled";
    private static final String PATH_AFK_NOTIFY_REPEAT = "settings.afk.notify.repeat";
    private static final String PATH_AFK_NOTIFY_TITLE = "settings.afk.notify.title";

    private static final String PATH_PERMISSIONS_ENABLED = "settings.permissions.enabled";
    private static final String PATH_PERMISSION_PREFIX = "settings.permissions.prefix";
    private static final String PATH_PERMISSION_BYPASS_AFK = "settings.permissions.bypass_afk";
    private static final String PATH_PERMISSION_RELOAD = "settings.permissions.reload";
    private static final String PATH_PERMISSION_LIST = "settings.permissions.list";

    private static final String PATH_SOUND_AFK_ENABLED = "settings.sounds.afk.enabled";
    private static final String PATH_SOUND_AFK_NAME = "settings.sounds.afk.sound";
    private static final String PATH_SOUND_AFK_VOLUME = "settings.sounds.afk.volume";
    private static final String PATH_SOUND_AFK_PITCH = "settings.sounds.afk.pitch";
    private static final String PATH_SOUND_SALARY_ENABLED = "settings.sounds.salary.enabled";
    private static final String PATH_SOUND_SALARY_NAME = "settings.sounds.salary.sound";
    private static final String PATH_SOUND_SALARY_VOLUME = "settings.sounds.salary.volume";
    private static final String PATH_SOUND_SALARY_PITCH = "settings.sounds.salary.pitch";

    private static final String PATH_GROUPS = "groups";

    private static final String PATH_MESSAGE_PREFIX = "messages.prefix";
    private static final String PATH_MESSAGES_ENABLED = "messages.on_salary.enabled";
    private static final String PATH_SALARY_TITLE = "messages.on_salary.title";
    private static final String PATH_SALARY_SUBTITLE = "messages.on_salary.subtitle";
    private static final String PATH_AFK_MESSAGE = "messages.on_afk.text";

    private static final String PATH_NO_PERMISSION = "messages.command.no_permission";
    private static final String PATH_USAGE = "messages.command.usage";
    private static final String PATH_UNKNOWN_PLAYER = "messages.command.unknown_player";
    private static final String PATH_RELOAD_SUCCESS = "messages.command.reload_success";
    private static final String PATH_INFO_SELF = "messages.command.info.self";
    private static final String PATH_INFO_OTHER = "messages.command.info.other";
    private static final String PATH_LIST_HEADER = "messages.command.list.header";
    private static final String PATH_LIST_ENTRY = "messages.command.list.entry";
    private static final String PATH_LIST_EMPTY = "messages.command.list.empty";
    private static final String PATH_STATUS_ONLINE = "messages.command.status.online";
    private static final String PATH_STATUS_AFK = "messages.command.status.afk";
    private static final String PATH_STATUS_OFFLINE = "messages.command.status.offline";
    private static final String PATH_STATUS_NO_GROUP = "messages.command.status.no_group";

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
    private boolean afkMessageRepeat;
    private AfkNotifyType afkNotifyType;

    private boolean permissionsEnabled;
    private String groupPermissionPrefix;
    private String permissionBypassAfk;
    private String permissionReload;
    private String permissionList;

    private SoundSettings afkSound;
    private SoundSettings salarySound;

    private long salaryIntervalSeconds;
    private String messagePrefix;
    private boolean messagesEnabled;
    private String salaryTitle;
    private String salarySubtitle;
    private List<String> afkMessage;
    private List<SalaryGroupSettings> groups;

    public ConfigManager(MoonSalary plugin) {

        this.plugin = plugin;
        saveDefaultConfig();

        loadConfig();
        cacheConfigValues();

    }

    private void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
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
        afkMessageRepeat = config.getBoolean(PATH_AFK_NOTIFY_REPEAT, true);
        afkNotifyType = resolveAfkNotifyType();

        permissionsEnabled = config.getBoolean(PATH_PERMISSIONS_ENABLED, true);
        groupPermissionPrefix = config.getString(PATH_PERMISSION_PREFIX, "group.");
        permissionBypassAfk = config.getString(PATH_PERMISSION_BYPASS_AFK, "moon_salary.bypass.afk");
        permissionReload = config.getString(PATH_PERMISSION_RELOAD, "moon_salary.admin.reload");
        permissionList = config.getString(PATH_PERMISSION_LIST, "moon_salary.admin.list");

        afkSound = loadSound("settings.sounds.afk",
                PATH_SOUND_AFK_ENABLED, PATH_SOUND_AFK_NAME,
                PATH_SOUND_AFK_VOLUME, PATH_SOUND_AFK_PITCH);
        salarySound = loadSound("settings.sounds.salary",
                PATH_SOUND_SALARY_ENABLED, PATH_SOUND_SALARY_NAME,
                PATH_SOUND_SALARY_VOLUME, PATH_SOUND_SALARY_PITCH);

        salaryIntervalSeconds = secondsOrWarn(
                config.getInt(PATH_INTERVAL, 3700), "settings.interval");

        messagePrefix = color(config.getString(PATH_MESSAGE_PREFIX, ""));
        messagesEnabled = config.getBoolean(PATH_MESSAGES_ENABLED, true);
        salaryTitle = color(config.getString(PATH_SALARY_TITLE, ""));
        salarySubtitle = color(config.getString(PATH_SALARY_SUBTITLE, ""));
        afkMessage = colorList(config.getStringList(PATH_AFK_MESSAGE));

        groups = loadGroups();

    }

    /**
     * Загружает список групп зарплат из секции groups.
     *
     * @return неизменяемый список настроек групп
     */
    private @NotNull List<SalaryGroupSettings> loadGroups() {

        ConfigurationSection section = config.getConfigurationSection(PATH_GROUPS);

        if (section == null) {

            plugin.getLogger().warning("Секция 'groups' не найдена - зарплаты выдаваться не будут.");
            return Collections.emptyList();

        }

        Set<String> keys = section.getKeys(false);
        List<SalaryGroupSettings> loadedGroups = new ArrayList<>(keys.size());

        for (String groupName : keys) {

            ConfigurationSection groupSection = section.getConfigurationSection(groupName);
            if (groupSection == null) continue;

            if (!groupSection.isSet("salary")) {

                plugin.getLogger().warning("Группа '" + groupName + "' не имеет поля 'salary' - пропущена.");
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
            plugin.getLogger().warning("Не найдено ни одной группы зарплат.");
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
                () -> plugin.getLogger().warning("Неизвестный звук в '" + logPath + ".sound'.")
        );

    }

    private @NotNull AfkNotifyType resolveAfkNotifyType() {

        if (!config.getBoolean(PATH_AFK_NOTIFY_ENABLED, true)) {
            return AfkNotifyType.NONE;
        }

        boolean title = config.getBoolean(PATH_AFK_NOTIFY_TITLE, false);
        return title ? AfkNotifyType.CHAT_TITLE : AfkNotifyType.CHAT;

    }

    private long secondsToTicks(int seconds, @NotNull String path) {
        return secondsOrWarn(seconds, path) * TICKS_PER_SECOND;
    }

    private long secondsToMillis(int seconds, @NotNull String path) {
        return secondsOrWarn(seconds, path) * MILLIS_PER_SECOND;
    }

    private long secondsOrWarn(int seconds, @NotNull String path) {

        if (seconds < MIN_INTERVAL_SECONDS) {

            plugin.getLogger().warning("Некорректное значение '" + path + "': " + seconds
                    + ". Используется: " + MIN_INTERVAL_SECONDS + ".");
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
                .collect(Collectors.toList());

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
    public boolean areAfkNotificationsEnabled() {
        return afkNotifyType != AfkNotifyType.NONE;
    }

    @Override
    public boolean shouldRepeatAfkMessage() {
        return afkMessageRepeat;
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
    public long getSalaryIntervalTicks() {
        return salaryIntervalSeconds * TICKS_PER_SECOND;
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
    public AfkNotifyType getAfkNotifyType() {
        return afkNotifyType;
    }

    @Override
    public SoundSettings getAfkSound() {
        return afkSound;
    }

    @Override
    public SoundSettings getSalarySound() {
        return salarySound;
    }

    @Override
    public String getGroupPermissionPrefix() {
        return groupPermissionPrefix;
    }

    @Override
    public String getPermissionBypassAfk() {
        return permissionBypassAfk;
    }

    @Override
    public String getPermissionReload() {
        return permissionReload;
    }

    @Override
    public String getPermissionList() {
        return permissionList;
    }

    @Override
    public String getMessagePrefix() {
        return messagePrefix;
    }

    @Override
    public String getSalaryTitle() {
        return salaryTitle;
    }

    @Override
    public String getSalarySubtitle() {
        return salarySubtitle;
    }

    @Override
    public List<String> getAfkMessage() {
        return afkMessage;
    }

    @Override
    public List<String> getInfoSelfMessage() {
        return colorList(config.getStringList(PATH_INFO_SELF));
    }

    @Override
    public List<String> getInfoOtherMessage() {
        return colorList(config.getStringList(PATH_INFO_OTHER));
    }

    @Override
    public String getListHeader() {
        return color(config.getString(PATH_LIST_HEADER, ""));
    }

    @Override
    public String getListEntry() {
        return color(config.getString(PATH_LIST_ENTRY, ""));
    }

    @Override
    public String getListEmpty() {
        return color(config.getString(PATH_LIST_EMPTY, ""));
    }

    @Override
    public String getStatusOnline() {
        return color(config.getString(PATH_STATUS_ONLINE, "&aонлайн"));
    }

    @Override
    public String getStatusAfk() {
        return color(config.getString(PATH_STATUS_AFK, "&cAFK"));
    }

    @Override
    public String getStatusOffline() {
        return color(config.getString(PATH_STATUS_OFFLINE, "&8оффлайн"));
    }

    @Override
    public String getStatusNoGroup() {
        return color(config.getString(PATH_STATUS_NO_GROUP, "&cгруппа не найдена"));
    }

    @Override
    public String getNoPermissionMessage() {
        return color(config.getString(PATH_NO_PERMISSION, ""));
    }

    @Override
    public String getUsageMessage() {
        return color(config.getString(PATH_USAGE, ""));
    }

    @Override
    public String getUnknownPlayerMessage() {
        return color(config.getString(PATH_UNKNOWN_PLAYER, ""));
    }

    @Override
    public String getReloadSuccessMessage() {
        return color(config.getString(PATH_RELOAD_SUCCESS, ""));
    }

    @Override
    public List<SalaryGroupSettings> getGroups() {
        return groups;
    }

    public void reload() {

        plugin.reloadConfig();

        loadConfig();
        cacheConfigValues();

    }
}
