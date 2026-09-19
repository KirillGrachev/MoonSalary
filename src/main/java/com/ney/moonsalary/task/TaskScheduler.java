package com.ney.moonsalary.task;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.MessageService;
import com.ney.moonsalary.service.PayoutSchedule;
import com.ney.moonsalary.service.SalaryPayoutService;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Планировщик задач плагина.
 * Все задачи работают в основном потоке сервера.
 */
public class TaskScheduler {

    /** Период проверки персональных сроков: 1 секунда */
    private static final long PERSONAL_CHECK_PERIOD_TICKS = 20L;

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final MessageService messageService;
    private final SalaryPayoutService payoutService;

    private @Nullable BukkitTask salaryTask;
    private @Nullable BukkitTask afkTask;

    public TaskScheduler(@NotNull MoonSalary plugin,
                         @NotNull ConfigManager configManager,
                         @NotNull GroupRegistry groupRegistry,
                         @NotNull AfkTracker afkTracker,
                         @NotNull EconomyService economyService,
                         @NotNull PayoutSchedule payoutSchedule,
                         @NotNull MessageService messageService,
                         @NotNull SalaryPayoutService payoutService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
        this.messageService = messageService;
        this.payoutService = payoutService;
    }

    /**
     * Запускает все задачи плагина.
     * Повторный вызов безопасен: старые задачи сначала отменяются.
     */
    public void start() {

        stop();

        startSalaryTask();
        startAfkTask();

    }

    /**
     * Проверяет, запущены ли задачи плагина.
     *
     * @return true если задачи активны
     */
    public boolean isRunning() {
        return salaryTask != null && !salaryTask.isCancelled();
    }

    /**
     * Перезапускает задачи (используется после /salary reload).
     */
    public void reschedule() {

        stop();
        start();

    }

    /**
     * Останавливает все задачи плагина.
     */
    public void stop() {

        cancel(salaryTask);
        cancel(afkTask);

        salaryTask = null;
        afkTask = null;

    }

    private void startSalaryTask() {

        if (configManager.getPayoutMode() == PayoutMode.PERSONAL) {
            startPersonalSalaryTask();
            return;
        }

        payoutSchedule.anchorGlobal(System.currentTimeMillis());

        GlobalSalaryTask task = new GlobalSalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService);

        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, task,
                configManager.getSalaryIntervalTicks(),
                configManager.getSalaryIntervalTicks());

    }

    /**
     * Персональный режим: один таск с периодом в секунду проверяет сроки
     * игроков по {@link PayoutSchedule}, вместо таска на каждого игрока.
     */
    private void startPersonalSalaryTask() {

        PersonalSalaryTask task = new PersonalSalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService);

        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, task,
                PERSONAL_CHECK_PERIOD_TICKS,
                PERSONAL_CHECK_PERIOD_TICKS);

    }

    private void startAfkTask() {

        AfkCheckTask task = new AfkCheckTask(configManager, afkTracker, economyService);

        afkTask = Bukkit.getScheduler().runTaskTimer(plugin, task,
                configManager.getAfkCheckIntervalTicks(),
                configManager.getAfkCheckIntervalTicks());

    }

    private void cancel(@Nullable BukkitTask task) {

        if (task != null) {
            task.cancel();
        }

    }
}
