package com.ney.moonsalary.task;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.PayoutSchedule;
import com.ney.moonsalary.service.SalaryPayoutService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Планировщик задач плагина.
 * <p>
 * Глобальный режим - один повторяющийся таск с периодом равным интервалу.
 * Персональный режим - event-driven: таск создаётся ровно к ближайшему
 * дедлайну выплаты ({@link PayoutSchedule}), обрабатывает должников и
 * пересоздаётся к следующему дедлайну. Между выплатами задач не существует,
 * поэтому простой сервера не стоит ничего.
 */
public class TaskScheduler {

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;

    private @Nullable BukkitTask salaryTask;
    private @Nullable BukkitTask afkTask;

    public TaskScheduler(@NotNull MoonSalary plugin,
                         @NotNull ConfigManager configManager,
                         @NotNull GroupRegistry groupRegistry,
                         @NotNull AfkTracker afkTracker,
                         @NotNull EconomyService economyService,
                         @NotNull PayoutSchedule payoutSchedule,
                         @NotNull SalaryPayoutService payoutService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
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
     * Перезапускает задачи (используется после /salary reload).
     */
    public void reschedule() {
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

    /**
     * Реакция на вход игрока: в персональном режиме игрок попадает
     * в расписание, а спящий планировщик просыпается.
     *
     * @param player вошедший игрок
     */
    public void onPlayerJoined(@NotNull Player player) {

        if (configManager.getPayoutMode() != PayoutMode.PERSONAL) {
            return;
        }

        payoutSchedule.track(player);

        // Новый дедлайн не может быть раньше уже запланированного,
        // поэтому перепланировка нужна только когда задач нет вовсе
        if (salaryTask == null || salaryTask.isCancelled()) {
            schedulePersonalPayout();
        }
    }

    /**
     * Реакция на выход игрока: дедлайн убирается из расписания.
     * Лишнее пробуждение планировщика безопасно: оно лишь пересоздаст задачу.
     *
     * @param player вышедший игрок
     */
    public void onPlayerQuit(@NotNull Player player) {
        payoutSchedule.remove(player);
    }

    /**
     * Планирует пробуждение ровно к ближайшему дедлайну выплаты.
     * Если должников нет (сервер пуст) - задача не создаётся до входа игрока.
     */
    public void schedulePersonalPayout() {

        cancel(salaryTask);
        salaryTask = null;

        long nearest = payoutSchedule.nearestDeadline(Bukkit.getOnlinePlayers());

        if (nearest == Long.MAX_VALUE) {
            return;
        }

        long now = System.currentTimeMillis();
        long delayTicks = Math.max(1L, (nearest - now + 49L) / 50L);

        PersonalSalaryTask task = new PersonalSalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService, this);

        salaryTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);

    }

    private void startSalaryTask() {

        if (configManager.getPayoutMode() == PayoutMode.PERSONAL) {

            for (Player player : Bukkit.getOnlinePlayers()) {
                payoutSchedule.track(player);
            }

            schedulePersonalPayout();
            return;

        }

        payoutSchedule.anchorGlobal(System.currentTimeMillis());

        GlobalSalaryTask task = new GlobalSalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService);

        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, task,
                configManager.getSalaryIntervalTicks(),
                configManager.getSalaryIntervalTicks());

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
