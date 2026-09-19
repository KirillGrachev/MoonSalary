package com.ney.moonsalary;

import com.ney.moonsalary.command.CommandDispatcher;
import com.ney.moonsalary.command.SalaryCommand;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.event.EventDispatcher;
import com.ney.moonsalary.listener.PlayerConnectionListener;
import com.ney.moonsalary.listener.VaultStateListener;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.ConsoleService;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.MessageService;
import com.ney.moonsalary.service.PayoutSchedule;
import com.ney.moonsalary.service.SalaryPayoutService;
import com.ney.moonsalary.task.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class MoonSalary extends JavaPlugin {

    private ConsoleService consoleService;
    private EconomyService economyService;
    private ConfigManager configManager;
    private GroupRegistry groupRegistry;
    private MessageService messageService;
    private AfkTracker afkTracker;
    private PayoutSchedule payoutSchedule;
    private SalaryPayoutService payoutService;
    private TaskScheduler taskScheduler;
    private CommandDispatcher commandDispatcher;

    /** Внутренний сбой при старте: плагин остаётся включённым, но не работает */
    private boolean startupFailed;

    @Override
    public void onEnable() {

        this.consoleService = new ConsoleService(this);

        try {

            initializeComponents();
            registerListeners();
            registerCommands();

            if (economyService.setup()) {

                startPayouts();

            } else {

                // Экономика может зарегистрироваться позже нас -
                // повторяем попытку на первом тике, когда все плагины уже включены
                consoleService.log(ConsoleMessage.ECONOMY_WAITING);
                Bukkit.getScheduler().runTask(this, this::retryEconomyHook);

            }

        } catch (Exception exception) {

            this.startupFailed = true;

            consoleService.log(ConsoleMessage.STARTUP_FAILED,
                    "reason", String.valueOf(exception.getMessage()));
            getLogger().log(Level.FINE, "Причина сбоя при включении", exception);

            Bukkit.getPluginManager().disablePlugin(this);

        }
    }

    @Override
    public void onDisable() {

        if (taskScheduler != null) {
            taskScheduler.stop();
        }

        if (commandDispatcher != null) {
            commandDispatcher.unregisterCommand("salary");
        }

        if (afkTracker != null) {
            afkTracker.clear();
        }

        if (payoutSchedule != null) {
            payoutSchedule.clear();
        }

        if (groupRegistry != null) {
            groupRegistry.clearRegisteredGroups();
        }

        if (economyService != null) {
            economyService.shutdown();
        }

        if (consoleService != null) {
            consoleService.log(ConsoleMessage.SHUTDOWN);
        }

    }

    /**
     * Создаёт все компоненты плагина.
     */
    private void initializeComponents() {

        this.economyService = new EconomyService(this, consoleService);
        this.configManager = new ConfigManager(this, consoleService);

        this.consoleService.attach(configManager);

        this.groupRegistry = new GroupRegistry(configManager);
        this.afkTracker = new AfkTracker(configManager);
        this.payoutSchedule = new PayoutSchedule(configManager);
        this.messageService = new MessageService(configManager, economyService,
                consoleService, payoutSchedule);
        this.payoutService = new SalaryPayoutService(this, configManager, economyService, messageService);

        this.taskScheduler = new TaskScheduler(this, configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService);

    }

    /**
     * Регистрирует слушателей плагина.
     */
    private void registerListeners() {

        new EventDispatcher(this).registerEvents(
                new PlayerConnectionListener(afkTracker, taskScheduler),
                new VaultStateListener(this, economyService, taskScheduler)
        );

    }

    /**
     * Регистрирует команды плагина.
     */
    private void registerCommands() {

        this.commandDispatcher = new CommandDispatcher(this, consoleService);

        commandDispatcher.registerCommand("salary",
                new SalaryCommand(this, configManager, groupRegistry,
                        afkTracker, messageService, taskScheduler)
        );

    }

    /**
     * Повторяет попытку подключения экономики после полного старта сервера.
     * Если экономики всё ещё нет - плагин отключается: без экономики
     * salary-плагин не работоспособен, а команда /salary вырегиструется,
     * чтобы не оставаться «зомби» у выключенного плагина.
     */
    private void retryEconomyHook() {

        if (economyService.setup()) {

            startPayouts();
            consoleService.log(ConsoleMessage.ECONOMY_LATE);
            return;

        }

        consoleService.log(ConsoleMessage.ECONOMY_MISSING);
        Bukkit.getPluginManager().disablePlugin(this);

    }

    /**
     * Запускает циклы выплат и проверки AFK.
     */
    private void startPayouts() {

        taskScheduler.start();

        if (!configManager.isEnabled()) {
            consoleService.log(ConsoleMessage.DISABLED_BY_CONFIG);
        }

        consoleService.log(ConsoleMessage.STARTUP,
                "groups", String.valueOf(groupRegistry.getRegisteredGroups().size()));

    }

    public boolean isStartupFailed() {
        return startupFailed;
    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GroupRegistry getGroupRegistry() {
        return groupRegistry;
    }

}
