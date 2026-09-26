package com.ney.moonsalary.task;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.ConsoleService;
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
 * <p>
 * Все методы вызываются из основного потока сервера.
 */
public class TaskScheduler {

    private final MoonSalary plugin;
    private final MoonSalaryConfig configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final PayoutSchedule payoutSchedule;
    private final SalaryPayoutService payoutService;
    private final ConsoleService consoleService;
    private final TickClock tickClock;

    private @Nullable BukkitTask salaryTask;
    private @Nullable BukkitTask afkTask;
    private @Nullable BukkitTask clockTask;

    /**
     * Дедлайн, к которому запланирован текущий персональный таск.
     * Нужен, чтобы вход игрока с более ранним (например, просроченным
     * и клампнутым в «сейчас») дедлайном переставлял пробуждение раньше.
     */
    private long scheduledPersonalDeadline = Long.MAX_VALUE;

    public TaskScheduler(@NotNull MoonSalary plugin,
                         @NotNull MoonSalaryConfig configManager,
                         @NotNull GroupRegistry groupRegistry,
                         @NotNull AfkTracker afkTracker,
                         @NotNull EconomyService economyService,
                         @NotNull PayoutSchedule payoutSchedule,
                         @NotNull SalaryPayoutService payoutService,
                         @NotNull ConsoleService consoleService,
                         @NotNull TickClock tickClock) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
        this.payoutSchedule = payoutSchedule;
        this.payoutService = payoutService;
        this.consoleService = consoleService;
        this.tickClock = tickClock;

    }

    /**
     * Запускает все задачи плагина.
     * Повторный вызов безопасен: старые задачи сначала отменяются.
     */
    public void start() {

        stop();
        startClockTask();
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
        cancel(clockTask);

        salaryTask = null;
        afkTask = null;
        clockTask = null;
        scheduledPersonalDeadline = Long.MAX_VALUE;

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

        // Сохранённое окно могло истечь оффлайн и при загрузке клампится
        // в «сейчас» - значит новый дедлайн МОЖЕТ быть раньше уже
        // запланированного пробуждения. Перепланировка нужна, если задачи
        // нет вовсе или если дедлайн вошедшего раньше текущего.
        Long deadline = payoutSchedule.nextDeadline(player);
        boolean noTask = salaryTask == null || salaryTask.isCancelled();
        boolean earlierThanScheduled = deadline != null && deadline < scheduledPersonalDeadline;

        if (noTask || earlierThanScheduled) {
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

        long now = payoutSchedule.currentTicks();
        long nearest = payoutSchedule.nearestDeadline(Bukkit.getOnlinePlayers());
        scheduledPersonalDeadline = nearest;

        if (nearest == Long.MAX_VALUE) {
            return;
        }

        long delayTicks = Math.max(1L, nearest - now);
        PersonalSalaryTask task = new PersonalSalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService, consoleService, this);

        salaryTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);

    }

    private void startClockTask() {
        clockTask = Bukkit.getScheduler().runTaskTimer(plugin, tickClock, 1L, 1L);
    }

    private void startSalaryTask() {

        if (configManager.getPayoutMode() == PayoutMode.PERSONAL) {

            for (Player player : Bukkit.getOnlinePlayers()) {
                payoutSchedule.track(player);
            }

            schedulePersonalPayout();
            return;

        }

        payoutSchedule.anchorGlobal(payoutSchedule.currentTicks());
        GlobalSalaryTask task = new GlobalSalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService, consoleService);

        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, task,
                configManager.getSalaryIntervalTicks(),
                configManager.getSalaryIntervalTicks());

    }

    private void startAfkTask() {
        AfkCheckTask task = new AfkCheckTask(configManager, afkTracker, payoutSchedule);
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
