package com.ney.moonsalary.task;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.event.PlayerAfkEvent;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
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
 * Точка отсчёта простоя создаётся при первом обнаружении игрока,
 * поэтому сразу после входа на сервер он не считается AFK.
 */
public class AfkCheckTask implements Runnable {

    private final ConfigManager configManager;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;

    public AfkCheckTask(@NotNull ConfigManager configManager,
                        @NotNull AfkTracker afkTracker,
                        @NotNull EconomyService economyService) {
        this.configManager = configManager;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
    }

    @Override
    public void run() {

        if (!configManager.isAfkEnabled() || !economyService.isAvailable()) {
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

        if (afkTracker.hasMoved(player)) {

            boolean wasAfk = afkTracker.markActive(player);

            afkTracker.startTracking(player);
            callAfkEvent(player, false, wasAfk);

            return;

        }

        afkTracker.startTracking(player);

        if (!afkTracker.isIdleTooLong(player)) {
            return;
        }

        if (afkTracker.markAfk(player)) {
            callAfkEvent(player, true, true);
        }
    }

    private void callAfkEvent(@NotNull Player player, boolean afk, boolean changed) {

        if (!changed) {
            return;
        }

        Bukkit.getPluginManager().callEvent(new PlayerAfkEvent(player, afk));

    }
}
