package com.ney.moonsalary.storage;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.StorageSettings;
import com.ney.moonsalary.config.type.StorageType;
import com.ney.moonsalary.storage.library.LibraryLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * Фабрика хранилища по выбранному в конфиге формату.
 * При недоступности SQL плагин продолжает работу на YAML
 * и пишет об этом в лог жирным предупреждением.
 */
public class PayoutRepositoryFactory {

    private static final String FILE_DATABASE_PATH = "storage/moonsalary";
    private static final String LIBS_FOLDER = "libs";

    private final MoonSalary plugin;
    private final ConfigManager configManager;

    public PayoutRepositoryFactory(@NotNull MoonSalary plugin, @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * Создаёт хранилище выбранного типа.
     *
     * @return готовое к работе хранилище
     */
    public @NotNull PayoutRepository create() {

        StorageSettings settings = configManager.getStorageSettings();
        YamlPayoutRepository yamlRepository = new YamlPayoutRepository(plugin);

        if (settings.type() == StorageType.YAML) {
            return yamlRepository;
        }

        try {

            SqlPayoutRepository sqlRepository = createSqlRepository(settings);
            importWindowsFromYamlIfEmpty(sqlRepository, yamlRepository);

            return sqlRepository;

        } catch (SQLException | RuntimeException exception) {

            plugin.getLogger().severe("SQL storage is unavailable: " + exception.getMessage()
                    + ". Falling back to YAML (payouts.yml).");
            return yamlRepository;

        }

    }

    private @NotNull SqlPayoutRepository createSqlRepository(@NotNull StorageSettings settings) throws SQLException {

        LibraryLoader libraryLoader = new LibraryLoader(
                new File(plugin.getDataFolder(), LIBS_FOLDER),
                configManager.isDownloadLibrariesEnabled()
        );

        ConnectionFactory connectionFactory = switch (settings.type()) {
            case MYSQL -> new MysqlConnectionFactory(settings.sql(), libraryLoader);
            case SQLITE -> new SqliteConnectionFactory(
                    new File(plugin.getDataFolder(), FILE_DATABASE_PATH + ".db"), libraryLoader);
            case H2 -> new H2ConnectionFactory(
                    new File(plugin.getDataFolder(), FILE_DATABASE_PATH), libraryLoader);
            case YAML -> throw new IllegalStateException("YAML is handled before SQL repository creation");
        };

        SqlPayoutRepository sqlRepository = new SqlPayoutRepository(
                connectionFactory,
                settings.sql(),
                settings.type(),
                plugin.getLogger()
        );

        sqlRepository.connect();
        return sqlRepository;

    }

    /**
     * Переносит окна выплат из payouts.yml в пустую базу при первом включении SQL.
     *
     * @param sqlRepository  SQL-хранилище
     * @param yamlRepository файловое хранилище
     */
    private void importWindowsFromYamlIfEmpty(@NotNull SqlPayoutRepository sqlRepository,
                                              @NotNull YamlPayoutRepository yamlRepository) {

        List<YamlPayoutRepository.NextPayout> yamlPayouts = yamlRepository.loadAllNextPayouts();

        if (yamlPayouts.isEmpty()) {
            return;
        }

        int imported = 0;

        for (YamlPayoutRepository.NextPayout next : yamlPayouts) {

            if (sqlRepository.loadNextPayout(next.playerId()) != null) {
                continue;
            }

            sqlRepository.saveNextPayout(next.playerId(), next.nextPayoutAt(), next.player());
            imported++;

        }

        if (imported > 0) {
            plugin.getLogger().info("Imported " + imported + " payout deadline(s) from payouts.yml into SQL.");
        }

    }

}
