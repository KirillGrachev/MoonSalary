package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.PayoutMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayoutScheduleTest {

    private static final long INTERVAL_MILLIS = 3600_000L;
    private static final long NOW = 1_000_000L;

    private ConfigManager configManager;
    private Player player;
    private PayoutSchedule payoutSchedule;

    @BeforeEach
    void setUp() {

        this.configManager = mock(ConfigManager.class);
        when(configManager.getSalaryIntervalMillis()).thenReturn(INTERVAL_MILLIS);
        when(configManager.getPayoutMode()).thenReturn(PayoutMode.PERSONAL);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        this.payoutSchedule = new PayoutSchedule(configManager);

    }

    @Test
    @DisplayName("Отсчёт стартует с момента постановки в расписание")
    void countsFromTrackMoment() {

        payoutSchedule.track(player, NOW);

        assertTrue(payoutSchedule.isTracked(player));
        assertFalse(payoutSchedule.isDue(player, NOW + INTERVAL_MILLIS - 1));
        assertTrue(payoutSchedule.isDue(player, NOW + INTERVAL_MILLIS));

    }

    @Test
    @DisplayName("Повторный track не сбивает установленный срок")
    void trackIsIdempotent() {

        payoutSchedule.track(player, NOW);
        payoutSchedule.track(player, NOW + 60_000L);

        assertTrue(payoutSchedule.isDue(player, NOW + INTERVAL_MILLIS));

    }

    @Test
    @DisplayName("После выплаты окно сдвигается без догоняющих выплат")
    void advancePreventsCatchUpPayouts() {

        payoutSchedule.track(player, NOW);
        payoutSchedule.advance(player, NOW + INTERVAL_MILLIS + 5_000L);

        assertFalse(payoutSchedule.isDue(player, NOW + INTERVAL_MILLIS + 5_000L));
        assertFalse(payoutSchedule.isDue(player, NOW + 2 * INTERVAL_MILLIS));
        assertTrue(payoutSchedule.isDue(player, NOW + 2 * INTERVAL_MILLIS + 5_000L));

    }

    @Test
    @DisplayName("Игрок вне расписания не получает выплат")
    void untrackedPlayerIsNeverDue() {

        assertFalse(payoutSchedule.isTracked(player));
        assertFalse(payoutSchedule.isDue(player, NOW + 10 * INTERVAL_MILLIS));

    }

    @Test
    @DisplayName("PERSONAL: отсчёт до личного окна, вне расписания - полный интервал")
    void personalCountdown() {

        when(configManager.getPayoutMode()).thenReturn(PayoutMode.PERSONAL);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW));

        payoutSchedule.track(player, NOW);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW));
        assertEquals(INTERVAL_MILLIS / 2, payoutSchedule.millisUntilNext(player, NOW + INTERVAL_MILLIS / 2));
        assertEquals(0L, payoutSchedule.millisUntilNext(player, NOW + 2 * INTERVAL_MILLIS));

    }

    @Test
    @DisplayName("GLOBAL: отсчёт до ближайшего payday от якоря")
    void globalCountdown() {

        when(configManager.getPayoutMode()).thenReturn(PayoutMode.GLOBAL);

        payoutSchedule.anchorGlobal(NOW);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW));

        payoutSchedule.advanceGlobal(NOW + INTERVAL_MILLIS);

        assertEquals(INTERVAL_MILLIS, payoutSchedule.millisUntilNext(player, NOW + INTERVAL_MILLIS));
        assertEquals(0L, payoutSchedule.millisUntilNext(player, NOW + 5 * INTERVAL_MILLIS));

    }

    @Test
    @DisplayName("nearestDeadline возвращает ближайший срок среди онлайна")
    void nearestDeadlineAmongOnline() {

        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());

        payoutSchedule.track(player, NOW);
        payoutSchedule.track(second, NOW + 60_000L);

        assertEquals(NOW + INTERVAL_MILLIS,
                payoutSchedule.nearestDeadline(java.util.List.of(player, second)));
        assertEquals(Long.MAX_VALUE, payoutSchedule.nearestDeadline(java.util.List.of()));

    }

    @Test
    @DisplayName("remove убирает игрока из расписания")
    void removeClearsEntry() {

        payoutSchedule.track(player, NOW);
        payoutSchedule.remove(player);

        assertFalse(payoutSchedule.isTracked(player));

    }
}