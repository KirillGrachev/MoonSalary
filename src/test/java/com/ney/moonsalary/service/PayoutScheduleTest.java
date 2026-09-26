package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.config.type.StorageType;
import com.ney.moonsalary.storage.PayoutEntry;
import com.ney.moonsalary.storage.PayoutRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayoutScheduleTest {

    private static final long INTERVAL_TICKS = 72_000L;
    private static final long INTERVAL_MILLIS = INTERVAL_TICKS * 50L;
    private static final long NOW = 1_000_000L;

    private final AtomicLong ticks = new AtomicLong(NOW);
    private final AtomicLong wall = new AtomicLong(10_000_000L);

    private final Map<UUID, Long> stored = new HashMap<>();

    /** In-memory хранилище: контракт PayoutRepository без файлов и базы. */
    private final PayoutRepository repository = new PayoutRepository() {

        @Override
        public @NotNull StorageType type() {
            return StorageType.YAML;
        }

        @Override
        public Long loadNextPayout(@NotNull UUID playerId) {
            return stored.get(playerId);
        }

        @Override
        public void saveNextPayout(@NotNull UUID playerId, long nextPayoutAt, @NotNull String playerName) {
            stored.put(playerId, nextPayoutAt);
        }

        @Override
        public @NotNull List<PayoutEntry> loadHistory(@NotNull UUID playerId, int limit) {
            return List.of();
        }

        @Override
        public void appendHistory(@NotNull List<PayoutEntry> entries, int limit) {
        }

        @Override
        public void close() {
        }

    };

    private ConfigManager configManager;
    private Player player;
    private PayoutSchedule payoutSchedule;

    @BeforeEach
    void setUp() {

        configManager = mock(ConfigManager.class);
        when(configManager.getSalaryIntervalTicks()).thenReturn(INTERVAL_TICKS);
        when(configManager.getPayoutMode()).thenReturn(PayoutMode.PERSONAL);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        this.payoutSchedule = new PayoutSchedule(configManager, repository,
                ticks::get, wall::get);

    }

    @Test
    @DisplayName("Отсчёт стартует с момента постановки в расписание")
    void countsFromTrackMoment() {

        payoutSchedule.track(player, NOW);

        assertTrue(payoutSchedule.isTracked(player));
        assertFalse(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS - 1));
        assertTrue(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS));

    }

    @Test
    @DisplayName("Повторный track не сбивает установленный срок")
    void trackIsIdempotent() {

        payoutSchedule.track(player, NOW);
        payoutSchedule.track(player, NOW + 1_200L);

        assertTrue(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS));

    }

    @Test
    @DisplayName("После выплаты окно сдвигается без догоняющих выплат")
    void advancePreventsCatchUpPayouts() {

        payoutSchedule.track(player, NOW);
        payoutSchedule.advance(player, NOW + INTERVAL_TICKS + 100L);

        assertFalse(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS + 100L));
        assertFalse(payoutSchedule.isDue(player, NOW + 2 * INTERVAL_TICKS));
        assertTrue(payoutSchedule.isDue(player, NOW + 2 * INTERVAL_TICKS + 100L));

    }

    @Test
    @DisplayName("Игрок вне расписания не получает выплат")
    void untrackedPlayerIsNeverDue() {
        assertFalse(payoutSchedule.isTracked(player));
        assertFalse(payoutSchedule.isDue(player, NOW + 10 * INTERVAL_TICKS));
    }

    @Test
    @DisplayName("PERSONAL: отсчёт до личного окна, вне расписания - полный интервал")
    void personalCountdown() {

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW));
        payoutSchedule.track(player, NOW);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW));
        assertEquals(INTERVAL_MILLIS / 2, payoutSchedule.millisUntilNext(player, NOW + INTERVAL_TICKS / 2));
        assertEquals(0L, payoutSchedule.millisUntilNext(player, NOW + 2 * INTERVAL_TICKS));

    }

    @Test
    @DisplayName("GLOBAL: отсчёт до ближайшего payday от якоря")
    void globalCountdown() {

        when(configManager.getPayoutMode()).thenReturn(PayoutMode.GLOBAL);
        payoutSchedule.anchorGlobal(NOW);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW));
        payoutSchedule.advanceGlobal(NOW + INTERVAL_TICKS);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW + INTERVAL_TICKS));
        assertEquals(0L, payoutSchedule.millisUntilNext(player, NOW + 5 * INTERVAL_TICKS));

    }

    @Test
    @DisplayName("nearestDeadline возвращает ближайший срок среди онлайна")
    void nearestDeadlineAmongOnline() {

        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        payoutSchedule.track(player, NOW);
        payoutSchedule.track(second, NOW + 1_200L);

        assertEquals(NOW + INTERVAL_TICKS, payoutSchedule.nearestDeadline(List.of(player, second)));
        assertEquals(Long.MAX_VALUE, payoutSchedule.nearestDeadline(List.of()));

    }

    @Test
    @DisplayName("Окно переживает рестарт: тики сервера сбрасываются, срок остаётся")
    void windowSurvivesRestart() {

        payoutSchedule.track(player, NOW);

        // рестарт: прошло 30 секунд реального времени, тики сервера обнулились
        wall.addAndGet(30_000L);
        ticks.set(500L);
        PayoutSchedule restarted = new PayoutSchedule(configManager, repository,
                ticks::get, wall::get);
        restarted.track(player, 500L);

        // осталось 3.6M мс - 30s = 3.57M мс = 71400 тиков от нового старта
        assertFalse(restarted.isDue(player, 500L + 71_399L));
        assertTrue(restarted.isDue(player, 500L + 71_400L));

    }

    @Test
    @DisplayName("Пауза за AFK сдвигает дедлайн на длительность простоя")
    void afkPausePostponesDeadline() {

        when(configManager.isPayoutPausedWhileAfk()).thenReturn(true);
        payoutSchedule.track(player, NOW);

        payoutSchedule.startAfkPause(player, NOW + 10);
        payoutSchedule.endAfkPause(player, NOW + 30);

        assertFalse(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS + 19));
        assertTrue(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS + 20));

    }

    @Test
    @DisplayName("Скольжение окна: дедлайн в AFK уезжает вперёд вместо выплаты")
    void slideOverAfkMovesDueWindow() {

        when(configManager.isPayoutPausedWhileAfk()).thenReturn(true);
        payoutSchedule.track(player, NOW);
        payoutSchedule.startAfkPause(player, NOW);

        assertTrue(payoutSchedule.slideOverAfk(player, NOW + INTERVAL_TICKS));
        assertFalse(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS));
        assertTrue(payoutSchedule.isDue(player, NOW + 2 * INTERVAL_TICKS));

    }

    @Test
    @DisplayName("С выключенным pause_while_afk пауза и скольжение не работают")
    void pauseDisabledKeepsOldBehavior() {

        when(configManager.isPayoutPausedWhileAfk()).thenReturn(false);
        payoutSchedule.track(player, NOW);

        payoutSchedule.startAfkPause(player, NOW);
        payoutSchedule.endAfkPause(player, NOW + 500);

        assertFalse(payoutSchedule.slideOverAfk(player, NOW + INTERVAL_TICKS));

        assertTrue(payoutSchedule.isDue(player, NOW + INTERVAL_TICKS));

    }

    @Test
    @DisplayName("remove убирает игрока из расписания, но не из персистентности")
    void removeClearsEntry() {

        payoutSchedule.track(player, NOW);
        payoutSchedule.remove(player);

        assertFalse(payoutSchedule.isTracked(player));
        assertTrue(stored.containsKey(player.getUniqueId()));

    }

}
