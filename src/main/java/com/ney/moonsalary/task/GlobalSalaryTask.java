package com.ney.moonsalary.task;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.ConsoleService;
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
 * <p>
 * Сбой выплаты на одном игроке изолирован и не влияет на остальных,
 * а якорь payday продвигается всегда - обратный отсчёт {next} не разъезжается.
 */
public class GlobalSalaryTask implements Runnable {

    private final MoonSalaryConfig configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;
    private final ConsoleService consoleService;

    public GlobalSalaryTask(@NotNull MoonSalaryConfig configManager,
                            @NotNull GroupRegistry groupRegistry,
                            @NotNull AfkTracker afkTracker,
                            @NotNull EconomyService economyService,
                            @NotNull PayoutSchedule payoutSchedule,
                            @NotNull SalaryPayoutService payoutService,
                            @NotNull ConsoleService consoleService) {
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
        this.payoutService = payoutService;
        this.consoleService = consoleService;
    }

    @Override
    public void run() {

        if (!configManager.isEnabled() || !economyService.isAvailable()) {
            return;
        }

        long now = payoutSchedule.currentTicks();

        for (Player player : Bukkit.getOnlinePlayers()) {

            try {

                SalaryGroup group = groupRegistry.getPlayerGroup(player);

                if (group == null) continue;
                payoutService.payout(player, group, afkTracker.resolveState(player));

            } catch (RuntimeException exception) {
                consoleService.log(ConsoleMessage.PAYOUT_ERROR,
                        "player", player.getName(),
                        "reason", String.valueOf(exception));
            }

        }

        // обратный отсчёт {next} всегда сходится с фактическим payday
        payoutSchedule.advanceGlobal(now);

    }

}
