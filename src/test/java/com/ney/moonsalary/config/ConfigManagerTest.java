package com.ney.moonsalary.config;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.config.type.StorageType;
import com.ney.moonsalary.service.ConsoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigManagerTest {

    private static final char SECTION = '\u00A7';

    @TempDir
    private Path dataFolder;
    private MoonSalary plugin;
    private ConsoleService consoleService;

    @BeforeEach
    void setUp() {

        plugin = mock(MoonSalary.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

        consoleService = mock(ConsoleService.class);

    }

    private ConfigManager newConfigManager(String yaml) throws IOException {
        return newConfigManager(yaml, null);
    }

    private ConfigManager newConfigManager(String yaml, String groupsYaml) throws IOException {

        if (yaml != null) {
            Files.writeString(dataFolder.resolve("config.yml"), yaml);
        }

        if (groupsYaml != null) {
            Files.writeString(dataFolder.resolve("groups.yml"), groupsYaml);
        }

        return new ConfigManager(plugin, consoleService);

    }

    @Test
    @DisplayName("Без файла конфигурации работают дефолты, а отсутствие groups предупреждается")
    void defaultsWhenFileMissing() throws IOException {

        ConfigManager configManager = newConfigManager(null);

        assertTrue(configManager.isEnabled());
        assertTrue(configManager.areCommandsEnabled());
        assertEquals(PayoutMode.GLOBAL, configManager.getPayoutMode());
        assertTrue(configManager.isPayoutPausedWhileAfk());
        assertEquals("default", configManager.getFallbackGroup());
        assertEquals(3600L, configManager.getSalaryIntervalSeconds());
        assertEquals(3600L * 20L, configManager.getSalaryIntervalTicks());
        assertEquals(3600L * 1000L, configManager.getSalaryIntervalMillis());
        assertEquals(300L * 1000L, configManager.getAfkThresholdMillis());
        assertTrue(configManager.getGroups().isEmpty());
        assertFalse(configManager.getSalarySound().isPlayable());

        verify(consoleService).log(ConsoleMessage.GROUPS_SECTION_MISSING);

    }

    @Test
    @DisplayName("Кастомные значения читаются, ошибки конфигурации предупреждаются")
    void customValuesAndWarnings() throws IOException {

        ConfigManager configManager = newConfigManager("""
                settings:
                  enabled: false
                  payout:
                    mode: personal
                    interval: 0
                    fallback_group: ''
                  afk:
                    threshold: 30
                  sounds:
                    salary:
                      sound: NOT_A_SOUND
                """, """
                groups:
                  g1:
                    salary: 250
                    priority: 3
                    commands: 'give {player} stone'
                    messages:
                      - '&6Hi'
                  g2:
                    priority: 9
                """);

        assertFalse(configManager.isEnabled());
        ConfigManager withMessages = configManager;
        assertEquals(PayoutMode.PERSONAL, withMessages.getPayoutMode());
        assertEquals("", withMessages.getFallbackGroup());
        assertEquals(1L, configManager.getSalaryIntervalSeconds());
        assertEquals(30L * 1000L, configManager.getAfkThresholdMillis());
        assertFalse(configManager.getSalarySound().isPlayable());

        List<SalaryGroupSettings> groups = configManager.getGroups();
        assertEquals(1, groups.size());
        assertEquals("g1", groups.get(0).name());
        assertEquals(250D, groups.get(0).salary());
        assertEquals(3, groups.get(0).priority());
        assertEquals(List.of("give {player} stone"), groups.get(0).commands());
        assertEquals(SECTION + "6Hi", groups.get(0).messages().get(0));

        verify(consoleService).log(ConsoleMessage.INVALID_VALUE,
                "path", "settings.payout.interval", "value", "0", "defaultValue", "1");
        verify(consoleService).log(ConsoleMessage.UNKNOWN_SOUND, "path", "settings.sounds.salary.sound");
        verify(consoleService).log(ConsoleMessage.GROUP_NO_SALARY, "group", "g2");

    }

    @Test
    @DisplayName("Некорректный режим выплат откатывается к GLOBAL")
    void invalidModeFallsBackToGlobal() throws IOException {

        ConfigManager configManager = newConfigManager("""
                settings:
                  payout:
                    mode: chaos
                """);

        assertEquals(PayoutMode.GLOBAL, configManager.getPayoutMode());
        assertTrue(configManager.isPayoutPausedWhileAfk());
        verify(consoleService).log(ConsoleMessage.INVALID_VALUE,
                "path", "settings.payout.mode", "value", "chaos", "defaultValue", "GLOBAL");

    }

    @Test
    @DisplayName("Список команд чистится от пустых значений")
    void blankCommandsFiltered() throws IOException {
        ConfigManager configManager = newConfigManager(null, """
                groups:
                  g:
                    salary: 10
                    commands:
                      - 'a'
                      - ''
                      - 'b'
                """);
        assertEquals(List.of("a", "b"), configManager.getGroups().get(0).commands());
    }

    @Test
    @DisplayName("reload перечитывает файл без пересоздания менеджера")
    void reloadPicksUpChanges() throws IOException {

        ConfigManager configManager = newConfigManager("settings:\n  payout:\n    interval: 100\n");
        assertEquals(100L, configManager.getSalaryIntervalSeconds());

        Files.writeString(dataFolder.resolve("config.yml"), "settings:\n  payout:\n    interval: 200\n");
        configManager.reload();
        assertEquals(200L, configManager.getSalaryIntervalSeconds());

    }

    @Test
    @DisplayName("Тексты игроков читаются из секции messages конфига")
    void playerMessagesComeFromConfig() throws IOException {

        ConfigManager configManager = newConfigManager("""
                messages:
                  on_salary:
                    title: '&6Pay'
                    subtitle: '#ff0000{money}'
                  command:
                    status:
                      afk: '&cAFK'
                      no_payout: '&8никогда'
                """);

        assertEquals(SECTION + "6Pay", configManager.getSalaryTitle());
        assertTrue(configManager.getSalarySubtitle().contains(SECTION + "x"));
        assertEquals(SECTION + "cAFK", configManager.getStatusAfk());
        assertEquals(SECTION + "8никогда", configManager.getStatusNoPayout());

    }

    @Test
    @DisplayName("Хранилище по умолчанию - YAML")
    void storageDefaultsToYaml() throws IOException {
        assertEquals(StorageType.YAML, newConfigManager(null).getStorageSettings().type());
        assertEquals(10, newConfigManager(null).getHistoryLimit());
        assertTrue(newConfigManager(null).isDownloadLibrariesEnabled());
    }

    @Test
    @DisplayName("Некорректный тип хранилища откатывается к YAML с предупреждением")
    void invalidStorageTypeFallsBackToYaml() throws IOException {

        ConfigManager configManager = newConfigManager("""
                settings:
                  storage:
                    type: chaos
                    history_limit: -3
                """);

        assertEquals(StorageType.YAML, configManager.getStorageSettings().type());
        assertEquals(10, configManager.getHistoryLimit());

        verify(consoleService).log(ConsoleMessage.INVALID_VALUE,
                "path", "settings.storage.type", "value", "chaos", "defaultValue", "YAML");
        verify(consoleService).log(ConsoleMessage.INVALID_VALUE,
                "path", "settings.storage.history_limit", "value", "-3", "defaultValue", "10");

    }

    @Test
    @DisplayName("Настройки SQL читаются из конфига")
    void sqlSettingsAreReadable() throws IOException {

        ConfigManager configManager = newConfigManager("""
                settings:
                  storage:
                    type: mysql
                    download_libraries: false
                    sql:
                      host: 'db.local'
                      port: 3307
                      database: 'salary'
                      table: 'ms'
                      user: 'pay'
                      password: 'secret'
                      properties:
                        useSSL: 'false'
                """);

        assertEquals(StorageType.MYSQL, configManager.getStorageSettings().type());
        assertFalse(configManager.isDownloadLibrariesEnabled());
        assertEquals("db.local", configManager.getStorageSettings().sql().host());
        assertEquals(3307, configManager.getStorageSettings().sql().port());
        assertEquals("ms", configManager.getStorageSettings().sql().table());
        assertEquals("false", configManager.getStorageSettings().sql().properties().get("useSSL"));

    }

}
