package com.ney.moonsalary.task;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.SalaryPayoutService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Цикл выдачи зарплат.
 * Работает в основном потоке, так как использует Vault и отправку пакетов.
 */
public class SalaryTask implements Runnable {

    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final SalaryPayoutService payoutService;

    public SalaryTask(@NotNull ConfigManager configManager,
                      @NotNull GroupRegistry groupRegistry,
                      @NotNull AfkTracker afkTracker,
                      @NotNull EconomyService economyService,
                      @NotNull SalaryPayoutService payoutService) {
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutService = payoutService;
    }

    @Override
    public void run() {

        if (!configManager.isEnabled() || !economyService.isAvailable()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            SalaryGroup group = groupRegistry.getPlayerGroup(player);
            if (group == null) continue;

            payoutService.payout(player, group,
                    resolveAfkState(configManager, afkTracker, player));

        }
    }

    /**
     * Определяет AFK-состояние игрока с учётом настройки защиты и права обхода.
     *
     * @param configManager конфигурация
     * @param afkTracker    трекер AFK
     * @param player        игрок
     * @return состояние AFK
     */
    public static @NotNull AfkState resolveAfkState(@NotNull ConfigManager configManager,
                                                    @NotNull AfkTracker afkTracker,
                                                    @NotNull Player player) {

        if (!configManager.isAfkEnabled()) {
            return AfkState.ACTIVE;
        }

        if (!afkTracker.isMarked(player)) {
            return AfkState.ACTIVE;
        }

        return afkTracker.hasAfkBypass(player) ? AfkState.BYPASSED : AfkState.AFK;

    }
}
