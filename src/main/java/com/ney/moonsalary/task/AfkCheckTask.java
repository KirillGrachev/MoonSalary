package com.ney.moonsalary.task;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.event.PlayerAfkEvent;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Проверка игроков на AFK.
 * <p>
 * Точка отсчёта простоя создаётся при первом обнаружении игрока,
 * поэтому сразу после входа на сервер он не считается AFK.
 */
public class AfkCheckTask implements Runnable {

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final AfkTracker afkTracker;
    private final MessageService messageService;

    public AfkCheckTask(@NotNull MoonSalary plugin,
                        @NotNull ConfigManager configManager,
                        @NotNull AfkTracker afkTracker,
                        @NotNull MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.afkTracker = afkTracker;
        this.messageService = messageService;
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

            notifyAfk(player);
            callAfkEvent(player, true, true);

        }
    }

    /**
     * Уведомляет игрока о переходе в AFK.
     *
     * @param player игрок
     */
    private void notifyAfk(@NotNull Player player) {

        if (!configManager.areAfkNotificationsEnabled()) {
            return;
        }

        messageService.sendAfkWarning(player);
        messageService.playSound(player, configManager.getAfkSound());

    }

    private void callAfkEvent(@NotNull Player player, boolean afk, boolean changed) {

        if (!changed) {
            return;
        }

        Bukkit.getPluginManager().callEvent(new PlayerAfkEvent(player, afk));

    }
}
