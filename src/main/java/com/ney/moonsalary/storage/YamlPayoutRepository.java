package com.ney.moonsalary.storage;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.StorageType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Хранилище окон выплат и истории в файле payouts.yml.
 * Формат читаемый: администратор видит окна и журнал прямо в папке плагина.
 */
public class YamlPayoutRepository implements PayoutRepository {

    private static final String FILE_NAME = "payouts.yml";
    private static final String SECTION_PLAYERS = "players";
    private static final String SECTION_HISTORY = "history";

    private final MoonSalary plugin;
    private final File payoutsFile;

    public YamlPayoutRepository(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
        this.payoutsFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    @Override
    public @NotNull StorageType type() {
        return StorageType.YAML;
    }

    /**
     * Дедлайн следующей выплаты игрока из файла.
     *
     * @param playerId     uuid игрока
     * @param player       ник игрока
     * @param nextPayoutAt момент следующей выплаты в миллисекундах
     */
    public record NextPayout(@NotNull UUID playerId, @NotNull String player, long nextPayoutAt) {
    }

    /**
     * Читает дедлайны всех игроков из файла (для импорта в SQL при смене хранилища).
     *
     * @return список дедлайнов
     */
    public synchronized @NotNull List<NextPayout> loadAllNextPayouts() {

        List<NextPayout> payouts = new ArrayList<>();
        ConfigurationSection section = playersSection();

        if (section == null) {
            return payouts;
        }

        for (String key : section.getKeys(false)) {

            ConfigurationSection playerSection = section.getConfigurationSection(key);

            if (playerSection == null || !playerSection.isSet("next_payout_at")) {
                continue;
            }

            UUID playerId = parseUuid(key);

            if (playerId == null) {
                continue;
            }

            payouts.add(new NextPayout(
                    playerId,
                    playerSection.getString("name", ""),
                    playerSection.getLong("next_payout_at")
            ));

        }

        return payouts;

    }

    private static @Nullable UUID parseUuid(@NotNull String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public synchronized @Nullable Long loadNextPayout(@NotNull UUID playerId) {

        ConfigurationSection section = playersSection();

        if (section == null) {
            return null;
        }

        ConfigurationSection playerSection = section.getConfigurationSection(playerId.toString());

        if (playerSection == null) {
            return null;
        }

        return playerSection.isSet("next_payout_at") ? playerSection.getLong("next_payout_at") : null;

    }

    @Override
    public synchronized void saveNextPayout(@NotNull UUID playerId, long nextPayoutAt, @NotNull String playerName) {

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(payoutsFile);

        configuration.set(SECTION_PLAYERS + "." + playerId + ".name", playerName);
        configuration.set(SECTION_PLAYERS + "." + playerId + ".next_payout_at", nextPayoutAt);

        save(configuration);

    }

    @Override
    public synchronized @NotNull List<PayoutEntry> loadHistory(@NotNull UUID playerId, int limit) {

        List<PayoutEntry> entries = new ArrayList<>();

        if (limit <= 0) {
            return entries;
        }

        for (PayoutEntry entry : readHistory(YamlConfiguration.loadConfiguration(payoutsFile), playerId)) {
            entries.add(0, entry);
            if (entries.size() == limit) {
                break;
            }
        }

        return entries;

    }

    @Override
    public synchronized void appendHistory(@NotNull List<PayoutEntry> entries, int limit) {

        if (entries.isEmpty() || limit <= 0) {
            return;
        }

        FileConfiguration configuration = YamlConfiguration.loadConfiguration(payoutsFile);

        for (Map.Entry<UUID, List<PayoutEntry>> perPlayer : groupByPlayer(entries).entrySet()) {

            List<PayoutEntry> updated = new ArrayList<>(readHistory(configuration, perPlayer.getKey()));
            updated.addAll(perPlayer.getValue());

            while (updated.size() > limit) {
                updated.remove(0);
            }

            String base = SECTION_HISTORY + "." + perPlayer.getKey();
            configuration.set(base, null);

            for (int i = 0; i < updated.size(); i++) {
                configuration.set(base + "." + i, entryMap(updated.get(i)));
            }

        }

        save(configuration);

    }

    /**
     * Читает историю игрока из конфигурации в порядке записи (старые первыми).
     */
    private @NotNull List<PayoutEntry> readHistory(@NotNull FileConfiguration configuration,
                                                   @NotNull UUID playerId) {

        List<PayoutEntry> entries = new ArrayList<>();
        ConfigurationSection playerSection =
                configuration.getConfigurationSection(SECTION_HISTORY + "." + playerId);

        if (playerSection == null) {
            return entries;
        }

        List<String> keys = new ArrayList<>(playerSection.getKeys(false));
        keys.sort(Comparator.comparingInt(YamlPayoutRepository::indexOr));

        for (String key : keys) {

            ConfigurationSection entry = playerSection.getConfigurationSection(key);

            if (entry != null) {
                entries.add(readEntry(playerId, entry));
            }

        }

        return entries;

    }

    private static int indexOr(@NotNull String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private @NotNull Map<UUID, List<PayoutEntry>> groupByPlayer(@NotNull List<PayoutEntry> entries) {

        Map<UUID, List<PayoutEntry>> grouped = new LinkedHashMap<>();

        for (PayoutEntry entry : entries) {
            grouped.computeIfAbsent(entry.playerId(), id -> new ArrayList<>()).add(entry);
        }

        return grouped;

    }

    @Override
    public synchronized void close() {
        // файловое хранилище не держит открытых ресурсов
    }

    private @NotNull PayoutEntry readEntry(@NotNull UUID playerId, @NotNull ConfigurationSection section) {
        return new PayoutEntry(
                playerId,
                section.getString("player", ""),
                section.getLong("time"),
                section.getDouble("amount"),
                section.getString("group", ""),
                PayoutOutcome.valueOf(section.getString("outcome", "PAID").toUpperCase(Locale.ROOT)),
                PayoutSource.valueOf(section.getString("source", "SCHEDULED").toUpperCase(Locale.ROOT))
        );
    }

    private @Nullable ConfigurationSection playersSection() {
        return YamlConfiguration.loadConfiguration(payoutsFile).getConfigurationSection(SECTION_PLAYERS);
    }

    private @Nullable ConfigurationSection historySection() {
        return YamlConfiguration.loadConfiguration(payoutsFile).getConfigurationSection(SECTION_HISTORY);
    }

    private void save(@NotNull FileConfiguration configuration) {
        try {
            configuration.save(payoutsFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to save " + FILE_NAME + ": " + exception.getMessage());
        }
    }

    /**
     * Преобразование записи истории в map-вид для YAML.
     */
    private static @NotNull java.util.Map<String, Object> entryMap(@NotNull PayoutEntry entry) {
        return java.util.Map.of(
                "player", entry.player(),
                "time", entry.paidAt(),
                "amount", entry.amount(),
                "group", entry.group(),
                "outcome", entry.outcome().name(),
                "source", entry.source().name()
        );
    }

}
