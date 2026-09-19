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
 * Разовое пробуждение персонального планировщика.
 * <p>
 * Задача живёт один тик: выплачивает всем, чей срок подошёл, и передаёт
 * планировщику эстафету к следующему дедлайну. Периодического опроса нет.
 */
public class PersonalSalaryTask implements Runnable {

    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;
    private final TaskScheduler taskScheduler;

    public PersonalSalaryTask(@NotNull ConfigManager configManager,
                              @NotNull GroupRegistry groupRegistry,
                              @NotNull AfkTracker afkTracker,
                              @NotNull EconomyService economyService,
                              @NotNull PayoutSchedule payoutSchedule,
                              @NotNull SalaryPayoutService payoutService,
                              @NotNull TaskScheduler taskScheduler) {
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
        this.payoutService = payoutService;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void run() {

        if (configManager.isEnabled() && economyService.isAvailable()) {

            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {

                if (!payoutSchedule.isTracked(player)) {

                    // Игрок онлайн, но вне расписания (reload, старт плагина) -
                    // точка отсчёта создаётся сейчас
                    payoutSchedule.track(player, now);
                    continue;

                }

                if (!payoutSchedule.isDue(player, now)) {
                    continue;
                }

                // Окно выплаты потребляется в любом случае: ни AFK, ни отмена
                // события не могут привести к повторной выплате в том же окне
                payoutSchedule.advance(player, now);

                SalaryGroup group = groupRegistry.getPlayerGroup(player);
                if (group == null) continue;

                payoutService.payout(player, group, afkTracker.resolveState(player));

            }
        }

        taskScheduler.schedulePersonalPayout();

    }
}
