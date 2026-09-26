package com.ney.moonsalary.storage;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.SqlSettings;
import com.ney.moonsalary.config.type.StorageSettings;
import com.ney.moonsalary.config.type.StorageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayoutRepositoryFactoryTest {

    @TempDir
    private Path dataFolder;

    private MoonSalary plugin;
    private ConfigManager configManager;

    @BeforeEach
    void setUp() {
        plugin = mock(MoonSalary.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        configManager = mock(ConfigManager.class);
    }

    @Test
    @DisplayName("YAML выбирается без попыток поднять драйверы")
    void yamlNeedsNoDrivers() {

        when(configManager.getStorageSettings()).thenReturn(
                new StorageSettings(StorageType.YAML, sql(), 10));

        PayoutRepository repository = new PayoutRepositoryFactory(plugin, configManager).create();

        assertEquals(StorageType.YAML, repository.type());

    }

    @Test
    @DisplayName("Недоступный SQL откатывается к YAML с предупреждением")
    void sqlFailureFallsBackToYaml() {

        when(configManager.getStorageSettings()).thenReturn(
                new StorageSettings(StorageType.MYSQL, sql(), 10));
        // скачивание выключено, драйверов в libs нет - SQL заведомо недоступен
        when(configManager.isDownloadLibrariesEnabled()).thenReturn(false);

        PayoutRepository repository = new PayoutRepositoryFactory(plugin, configManager).create();

        assertEquals(StorageType.YAML, repository.type());

    }

    @Test
    @DisplayName("SQLite поднимается из скачанного драйвера")
    void sqliteRepositoryIsCreated() {

        when(configManager.getStorageSettings()).thenReturn(
                new StorageSettings(StorageType.SQLITE, sql(), 10));
        when(configManager.isDownloadLibrariesEnabled()).thenReturn(true);

        PayoutRepository repository = new PayoutRepositoryFactory(plugin, configManager).create();

        assertEquals(StorageType.SQLITE, repository.type());
        repository.close();

    }

    private SqlSettings sql() {
        return new SqlSettings("localhost", 3306, "moonsalary",
                "moonsalary", "root", "", Map.of());
    }

}
