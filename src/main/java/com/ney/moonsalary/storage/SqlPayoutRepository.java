package com.ney.moonsalary.storage;

import com.ney.moonsalary.config.type.SqlSettings;
import com.ney.moonsalary.config.type.StorageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Хранилище окон выплат и истории в SQL-базе (SQLite, MySQL, H2).
 * Подключение открывается лениво и переподнимается при обрыве.
 * <p>
 * Диалекты отличаются только upsert-ом окна: MySQL и H2 (режим MySQL)
 * используют ON DUPLICATE KEY UPDATE, SQLite - ON CONFLICT DO UPDATE.
 */
public class SqlPayoutRepository implements PayoutRepository {

    private static final String TABLE_PATTERN = "[A-Za-z0-9_]+";

    private final ConnectionFactory connectionFactory;
    private final SqlSettings settings;
    private final StorageType storageType;
    private final Logger logger;

    private Connection connection;

    public SqlPayoutRepository(@NotNull ConnectionFactory connectionFactory,
                               @NotNull SqlSettings settings,
                               @NotNull StorageType storageType,
                               @NotNull Logger logger) {
        this.connectionFactory = connectionFactory;
        this.settings = settings;
        this.storageType = storageType;
        this.logger = logger;
    }

    @Override
    public @NotNull StorageType type() {
        return storageType;
    }

    /**
     * Поднимает подключение и создаёт таблицы, если их ещё нет.
     *
     * @throws SQLException если база недоступна
     */
    public void connect() throws SQLException {

        Connection current = connection();

        try (Statement statement = current.createStatement()) {
            statement.executeUpdate(createPlayersTableQuery());
            statement.executeUpdate(createHistoryTableQuery());
        }

    }

    @Override
    public synchronized @Nullable Long loadNextPayout(@NotNull UUID playerId) {

        String query = "SELECT next_payout_at FROM " + playersTable() + " WHERE uuid = ?";

        try (PreparedStatement statement = connection().prepareStatement(query)) {

            statement.setString(1, playerId.toString());

            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("next_payout_at") : null;
            }

        } catch (SQLException exception) {
            logger.severe("Failed to load payout window of " + playerId + ": " + exception.getMessage());
            return null;
        }

    }

    @Override
    public synchronized void saveNextPayout(@NotNull UUID playerId, long nextPayoutAt, @NotNull String playerName) {

        try (PreparedStatement statement = connection().prepareStatement(upsertNextPayoutQuery())) {

            statement.setString(1, playerId.toString());
            statement.setString(2, playerName);
            statement.setLong(3, nextPayoutAt);
            statement.executeUpdate();

        } catch (SQLException exception) {
            logger.severe("Failed to save payout window of " + playerId + ": " + exception.getMessage());
        }

    }

    @Override
    public synchronized @NotNull List<PayoutEntry> loadHistory(@NotNull UUID playerId, int limit) {

        List<PayoutEntry> entries = new ArrayList<>();

        if (limit <= 0) {
            return entries;
        }

        String query = "SELECT player, paid_at, amount, group_name, outcome, source FROM " + historyTable()
                + " WHERE uuid = ? ORDER BY paid_at DESC, id DESC LIMIT " + limit;

        try (PreparedStatement statement = connection().prepareStatement(query)) {

            statement.setString(1, playerId.toString());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new PayoutEntry(
                            playerId,
                            result.getString("player"),
                            result.getLong("paid_at"),
                            result.getDouble("amount"),
                            result.getString("group_name"),
                            PayoutOutcome.valueOf(result.getString("outcome")),
                            PayoutSource.valueOf(result.getString("source"))
                    ));
                }
            }

        } catch (SQLException exception) {
            logger.severe("Failed to load payout history of " + playerId + ": " + exception.getMessage());
        }

        return entries;

    }

    @Override
    public synchronized void appendHistory(@NotNull List<PayoutEntry> entries, int limit) {

        if (entries.isEmpty() || limit <= 0) {
            return;
        }

        String insert = "INSERT INTO " + historyTable()
                + " (uuid, player, paid_at, amount, group_name, outcome, source) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try {

            Connection current = connection();

            try (PreparedStatement statement = current.prepareStatement(insert)) {

                for (PayoutEntry entry : entries) {

                    statement.setString(1, entry.playerId().toString());
                    statement.setString(2, entry.player());
                    statement.setLong(3, entry.paidAt());
                    statement.setDouble(4, entry.amount());
                    statement.setString(5, entry.group());
                    statement.setString(6, entry.outcome().name());
                    statement.setString(7, entry.source().name());
                    statement.addBatch();

                }

                statement.executeBatch();

            }

            trimHistory(entries.stream().map(PayoutEntry::playerId).collect(Collectors.toCollection(LinkedHashSet::new)), limit);

        } catch (SQLException exception) {
            logger.severe("Failed to append payout history: " + exception.getMessage());
        }

    }

    @Override
    public synchronized void close() {

        if (connection == null) {
            return;
        }

        try {
            connection.close();
        } catch (SQLException exception) {
            logger.warning("Failed to close SQL connection: " + exception.getMessage());
        } finally {
            connection = null;
        }

    }

    /**
     * Обрезает историю игроков сверх лимита: держим id свежих записей
     * и удаляем хвост одним IN-запросом (работает во всех трёх диалектах).
     */
    private void trimHistory(@NotNull Set<UUID> players, int limit) throws SQLException {

        for (UUID playerId : players) {

            List<Long> ids = new ArrayList<>();
            String select = "SELECT id FROM " + historyTable() + " WHERE uuid = ? ORDER BY paid_at DESC, id DESC";

            try (PreparedStatement statement = connection().prepareStatement(select)) {

                statement.setString(1, playerId.toString());

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        ids.add(result.getLong("id"));
                    }
                }

            }

            if (ids.size() <= limit) {
                continue;
            }

            String placeholders = ids.subList(limit, ids.size()).stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(", "));

            String delete = "DELETE FROM " + historyTable() + " WHERE id IN (" + placeholders + ")";

            try (PreparedStatement statement = connection().prepareStatement(delete)) {

                int index = 1;
                for (Long id : ids.subList(limit, ids.size())) {
                    statement.setLong(index++, id);
                }

                statement.executeUpdate();

            }

        }

    }

    private @NotNull String upsertNextPayoutQuery() {

        if (storageType == StorageType.SQLITE) {
            return "INSERT INTO " + playersTable() + " (uuid, name, next_payout_at) VALUES (?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, next_payout_at = excluded.next_payout_at";
        }

        return "INSERT INTO " + playersTable() + " (uuid, name, next_payout_at) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), next_payout_at = VALUES(next_payout_at)";

    }

    private @NotNull String createPlayersTableQuery() {

        return "CREATE TABLE IF NOT EXISTS " + playersTable() + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "name VARCHAR(16), "
                + "next_payout_at BIGINT NOT NULL)";

    }

    private @NotNull String createHistoryTableQuery() {

        String autoIncrement = storageType == StorageType.SQLITE
                ? "INTEGER PRIMARY KEY AUTOINCREMENT"
                : "BIGINT PRIMARY KEY AUTO_INCREMENT";

        return "CREATE TABLE IF NOT EXISTS " + historyTable() + " ("
                + "id " + autoIncrement + ", "
                + "uuid VARCHAR(36) NOT NULL, "
                + "player VARCHAR(16), "
                + "paid_at BIGINT NOT NULL, "
                + "amount DOUBLE NOT NULL, "
                + "group_name VARCHAR(64), "
                + "outcome VARCHAR(16) NOT NULL, "
                + "source VARCHAR(16) NOT NULL)";

    }

    private @NotNull String playersTable() {
        return table() + "_players";
    }

    private @NotNull String historyTable() {
        return table() + "_history";
    }

    private @NotNull String table() {

        String name = settings.table();

        if (name == null || !name.matches(TABLE_PATTERN)) {
            throw new IllegalArgumentException("Invalid SQL table name: " + name);
        }

        return name;

    }

    private Connection connection() throws SQLException {

        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            connection = connectionFactory.open();
        }

        return connection;

    }

}
