package com.ney.moonsalary.task;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.AfkState;
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
 * Разовое пробуждение персонального планировщика.
 * <p>
 * Задача живёт один тик: выплачивает всем, чей срок подошёл, и передаёт
 * планировщику эстафету к следующему дедлайну. Периодического опроса нет.
 * <p>
 * Эстафета передаётся в {@code finally}, а сбой на одном игроке изолирован
 * try/catch: ни чужой кидающий плагин, ни ошибка выплаты не могут оборвать
 * цепочку пробуждений - иначе персональные выплаты остановились бы до reload.
 */
public class PersonalSalaryTask implements Runnable {

    private final MoonSalaryConfig configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;
    private final ConsoleService consoleService;
    private final TaskScheduler taskScheduler;

    public PersonalSalaryTask(@NotNull MoonSalaryConfig configManager,
                              @NotNull GroupRegistry groupRegistry,
                              @NotNull AfkTracker afkTracker,
                              @NotNull EconomyService economyService,
                              @NotNull PayoutSchedule payoutSchedule,
                              @NotNull SalaryPayoutService payoutService,
                              @NotNull ConsoleService consoleService,
                              @NotNull TaskScheduler taskScheduler) {
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
        this.payoutService = payoutService;
        this.consoleService = consoleService;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void run() {

        try {

            if (configManager.isEnabled() && economyService.isAvailable()) {

                long now = payoutSchedule.currentTicks();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        processPlayer(player, now);
                    } catch (RuntimeException exception) {
                        consoleService.log(ConsoleMessage.PAYOUT_ERROR,
                                "player", player.getName(),
                                "reason", String.valueOf(exception));
                    }
                }

            }

        } finally {
            taskScheduler.schedulePersonalPayout();
        }

    }

    /**
     * Обрабатывает одного игрока: постановка в расписание либо выплата
     * подошедшего окна.
     *
     * @param player игрок
     * @param now    текущий тик
     */
    private void processPlayer(@NotNull Player player, long now) {

        if (!payoutSchedule.isTracked(player)) {

            // Игрок онлайн, но вне расписания (reload, старт плагина) -
            // точка отсчёта создаётся сейчас
            payoutSchedule.track(player, now);
            return;

        }

        if (!payoutSchedule.isDue(player, now)) {
            return;
        }

        SalaryGroup group = groupRegistry.getPlayerGroup(player);

        if (group == null) {

            // Окно потребляется даже без группы: догоняющих выплат не бывает
            payoutSchedule.advance(player, now);
            return;

        }

        AfkState afkState = afkTracker.resolveState(player);

        // Игрок стоит в AFK: время простоя не считается, окно скользит вперёд
        if (afkState == AfkState.AFK && payoutSchedule.slideOverAfk(player, now)) {
            return;
        }

        // Окно выплаты потребляется в любом случае: ни AFK, ни отмена
        // события не могут привести к повторной выплате в том же окне
        payoutSchedule.advance(player, now);
        payoutService.payout(player, group, afkState);

    }

}
