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
 * Персональный цикл выплат: у каждого игрока свой таймер, который
 * стартует с момента захода на сервер.
 * <p>
 * Задача лишь раз в секунду смотрит, у кого подошёл срок, поэтому
 * количество игроков не влияет на число запланированных тасков.
 */
public class PersonalSalaryTask implements Runnable {

    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;

    public PersonalSalaryTask(@NotNull ConfigManager configManager,
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

            if (!payoutSchedule.isTracked(player)) {

                // Игрок онлайн, но в расписании его нет (старте плагина, reload) -
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
}
