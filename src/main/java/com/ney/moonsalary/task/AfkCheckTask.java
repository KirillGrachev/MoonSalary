package com.ney.moonsalary.task;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.event.PlayerAfkEvent;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.PayoutSchedule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Проверка игроков на AFK.
 * <p>
 * Работает полностью тихо: никаких сообщений, звуков и тайтлов игроку.
 * Результат проверки влияет только на выдачу зарплаты и на событие
 * {@link PlayerAfkEvent} для других плагинов, поэтому MoonSalary
 * не конфликтует с посторонними AFK-плагинами.
 * <p>
 * Трекинг не зависит от доступности экономики: AFK-состояние - факт об
 * игроке, а не о выплатах. Когда экономика на паузе, задачи плагина
 * остановлены целиком, и проверка просто не выполняется.
 * <p>
 * Точка отсчёта простоя создаётся при первом обнаружении игрока,
 * поэтому сразу после входа на сервер он не считается AFK.
 */
public class AfkCheckTask implements Runnable {

    private final MoonSalaryConfig configManager;
    private final AfkTracker afkTracker;
    private final PayoutSchedule payoutSchedule;

    public AfkCheckTask(@NotNull MoonSalaryConfig configManager,
                        @NotNull AfkTracker afkTracker,
                        @NotNull PayoutSchedule payoutSchedule) {
        this.configManager = configManager;
        this.afkTracker = afkTracker;
        this.payoutSchedule = payoutSchedule;
    }

    @Override
    public void run() {

        if (!configManager.isAfkEnabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player);
        }

    }

    /**
     * Обновляет AFK-состояние одного игрока.
     *
     * @param player игрок
     */
    private void checkPlayer(@NotNull Player player) {

        Boolean becameAfk = afkTracker.processCheck(player,
                configManager.getAfkCheckIntervalTicks());

        if (becameAfk != null) {

            // переходы AFK управляют паузой персонального окна:
            // время простоя не приближает выплату
            long now = payoutSchedule.currentTicks();

            if (becameAfk) {
                payoutSchedule.startAfkPause(player, now);
            } else {
                payoutSchedule.endAfkPause(player, now);
            }

            Bukkit.getPluginManager().callEvent(new PlayerAfkEvent(player, becameAfk));

        }

    }

}
