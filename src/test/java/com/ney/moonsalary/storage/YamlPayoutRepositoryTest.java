package com.ney.moonsalary.storage;

import com.ney.moonsalary.MoonSalary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YamlPayoutRepositoryTest {

    @TempDir
    private Path dataFolder;

    private YamlPayoutRepository repository;

    @BeforeEach
    void setUp() {
        MoonSalary plugin = mock(MoonSalary.class);
        when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        repository = new YamlPayoutRepository(plugin);
    }

    @Test
    @DisplayName("Окно сохраняется и перечитывается")
    void windowRoundTrip() {

        UUID playerId = UUID.randomUUID();

        assertNull(repository.loadNextPayout(playerId));

        repository.saveNextPayout(playerId, 1_700_000_000_000L, "Ney");

        assertEquals(1_700_000_000_000L, repository.loadNextPayout(playerId));
        repository.saveNextPayout(playerId, 1_800_000_000_000L, "Ney");
        assertEquals(1_800_000_000_000L, repository.loadNextPayout(playerId));

    }

    @Test
    @DisplayName("loadAllNextPayouts читает дедлайны всех игроков для импорта")
    void loadsAllWindows() {

        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        repository.saveNextPayout(first, 111L, "A");
        repository.saveNextPayout(second, 222L, "B");

        List<YamlPayoutRepository.NextPayout> payouts = repository.loadAllNextPayouts();

        assertEquals(2, payouts.size());
        assertTrue(payouts.stream().anyMatch(entry -> entry.playerId().equals(first) && entry.player().equals("A")));

    }

    @Test
    @DisplayName("История обрезается по лимиту, свежие записи первыми")
    void historyIsTrimmedAndOrdered() {

        UUID playerId = UUID.randomUUID();

        for (int i = 1; i <= 5; i++) {
            repository.appendHistory(List.of(
                    new PayoutEntry(playerId, "Ney", i * 1000L, i * 10D, "vip", PayoutOutcome.PAID, PayoutSource.SCHEDULED)), 3);
        }

        List<PayoutEntry> history = repository.loadHistory(playerId, 10);

        assertEquals(3, history.size());
        assertEquals(5000L, history.get(0).paidAt());
        assertEquals(3000L, history.get(2).paidAt());

    }

    @Test
    @DisplayName("Файл хранилища создаётся в папке плагина")
    void fileIsCreatedInPluginFolder() {
        repository.saveNextPayout(UUID.randomUUID(), 42L, "Ney");
        assertTrue(new File(dataFolder.toFile(), "payouts.yml").isFile());
    }

}
