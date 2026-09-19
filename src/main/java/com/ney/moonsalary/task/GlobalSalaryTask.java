package com.ney.moonsalary.task;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.PayoutSchedule;
import com.ney.moonsalary.service.SalaryPayoutService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Глобальный цикл выплат (payday): каждые N секунд зарплату получают
 * все игроки с группой одновременно.
 * Работает в основном потоке, так как использует Vault и отправку пакетов.
 */
public class GlobalSalaryTask implements Runnable {

    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;

    public GlobalSalaryTask(@NotNull ConfigManager configManager,
                            @NotNull GroupRegistry groupRegistry,
                            @NotNull AfkTracker afkTracker,
                            @NotNull EconomyService economyService,
                            @NotNull PayoutSchedule payoutSchedule,
                            @NotNull SalaryPayoutService payoutService) {
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
        this.payoutService = payoutService;
    }

    @Override
    public void run() {

        if (!configManager.isEnabled() || !economyService.isAvailable()) {
            return;
        }

        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {

            SalaryGroup group = groupRegistry.getPlayerGroup(player);
            if (group == null) continue;

            payoutService.payout(player, group, afkTracker.resolveState(player));

        }

        // обратный отсчёт {next} всегда сходится с фактическим payday
        payoutSchedule.advanceGlobal(now);

    }
}
