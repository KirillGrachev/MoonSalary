package com.ney.moonsalary.task;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.MessageService;
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

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final EconomyService economyService;
    private final MessageService messageService;
    private final SalaryPayoutService payoutService;

    private @Nullable BukkitTask salaryTask;
    private @Nullable BukkitTask afkTask;

    public TaskScheduler(@NotNull MoonSalary plugin,
                         @NotNull ConfigManager configManager,
                         @NotNull GroupRegistry groupRegistry,
                         @NotNull AfkTracker afkTracker,
                         @NotNull EconomyService economyService,
                         @NotNull MessageService messageService,
                         @NotNull SalaryPayoutService payoutService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.economyService = economyService;
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

        SalaryTask task = new SalaryTask(configManager, groupRegistry,
                afkTracker, economyService, payoutService);

        salaryTask = Bukkit.getScheduler().runTaskTimer(plugin, task,
                configManager.getSalaryIntervalTicks(),
                configManager.getSalaryIntervalTicks());

    }

    private void startAfkTask() {

        AfkCheckTask task = new AfkCheckTask(plugin, configManager, afkTracker, messageService);

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
