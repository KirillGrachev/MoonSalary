package com.ney.moonsalary.storage;

import com.ney.moonsalary.config.type.SqlSettings;
import com.ney.moonsalary.config.type.StorageType;
import com.ney.moonsalary.storage.library.LibraryLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * SQL-репозиторий на живых драйверах: H2 и SQLite поднимаются тем же путём,
 * что и на продакшене - драйвер скачивается в libs и грузится отдельным
 * classloader'ом. MySQL-диалект проверяется H2 в режиме совместимости.
 */
class SqlPayoutRepositoryTest {

    @TempDir
    private Path workDir;

    private SqlPayoutRepository repository;

    @AfterEach
    void tearDown() {
        if (repository != null) {
            repository.close();
        }
    }

    private SqlPayoutRepository repository(StorageType type) throws Exception {

        LibraryLoader libraryLoader = new LibraryLoader(workDir.resolve("libs").toFile(), true);
        File database = workDir.resolve("storage/test").toFile();

        ConnectionFactory factory = type == StorageType.SQLITE
                ? new SqliteConnectionFactory(database, libraryLoader)
                : new H2ConnectionFactory(database, libraryLoader);

        SqlSettings settings = new SqlSettings("localhost", 3306, "test",
                "moonsalary", "root", "", Map.of());

        SqlPayoutRepository created = new SqlPayoutRepository(
                factory, settings, type, Logger.getAnonymousLogger());
        created.connect();
        return created;

    }

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"SQLITE", "H2"})
    @DisplayName("Окно сохраняется и перечитывается")
    void windowRoundTrip(StorageType type) throws Exception {

        repository = repository(type);
        UUID playerId = UUID.randomUUID();

        assertNull(repository.loadNextPayout(playerId));
        repository.saveNextPayout(playerId, 123_456L, "Ney");

        assertEquals(123_456L, repository.loadNextPayout(playerId));

        repository.saveNextPayout(playerId, 789_012L, "Ney");

        assertEquals(789_012L, repository.loadNextPayout(playerId));

    }

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"SQLITE", "H2"})
    @DisplayName("История пишется batch-ем, читается новыми вперёд и обрезается")
    void historyRoundTripAndTrim(StorageType type) throws Exception {

        repository = repository(type);
        UUID playerId = UUID.randomUUID();

        repository.appendHistory(List.of(
                new PayoutEntry(playerId, "Ney", 1000L, 100D, "default", PayoutOutcome.PAID, PayoutSource.SCHEDULED),
                new PayoutEntry(playerId, "Ney", 2000L, 100D, "default", PayoutOutcome.BLOCKED_AFK, PayoutSource.SCHEDULED),
                new PayoutEntry(playerId, "Ney", 3000L, 100D, "vip", PayoutOutcome.PAID, PayoutSource.SCHEDULED)
        ), 2);

        List<PayoutEntry> history = repository.loadHistory(playerId, 10);

        assertEquals(2, history.size());
        assertEquals(3000L, history.get(0).paidAt());
        assertEquals(PayoutOutcome.BLOCKED_AFK, history.get(1).outcome());
        assertEquals("vip", history.get(0).group());

    }

    @ParameterizedTest
    @EnumSource(value = StorageType.class, names = {"SQLITE", "H2"})
    @DisplayName("Истории разных игроков не смешиваются")
    void historyIsPerPlayer(StorageType type) throws Exception {

        repository = repository(type);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        repository.appendHistory(List.of(
                new PayoutEntry(first, "A", 1000L, 1D, "g", PayoutOutcome.PAID, PayoutSource.SCHEDULED)), 10);
        repository.appendHistory(List.of(
                new PayoutEntry(second, "B", 2000L, 2D, "g", PayoutOutcome.FAILED, PayoutSource.SCHEDULED)), 10);

        assertEquals(1, repository.loadHistory(first, 10).size());
        assertEquals(PayoutOutcome.FAILED, repository.loadHistory(second, 10).get(0).outcome());

    }

    @Test
    @DisplayName("type() возвращает формат хранилища")
    void reportsOwnType() throws Exception {
        repository = repository(StorageType.H2);

        assertEquals(StorageType.H2, repository.type());
    }

}
