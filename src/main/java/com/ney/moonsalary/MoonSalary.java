package com.ney.moonsalary;

import com.ney.moonsalary.command.CommandDispatcher;
import com.ney.moonsalary.command.SalaryCommand;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.event.EventDispatcher;
import com.ney.moonsalary.listener.PlayerConnectionListener;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.MessageService;
import com.ney.moonsalary.service.SalaryPayoutService;
import com.ney.moonsalary.task.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoonSalary extends JavaPlugin {

    private EconomyService economyService;
    private ConfigManager configManager;
    private GroupRegistry groupRegistry;
    private MessageService messageService;
    private AfkTracker afkTracker;
    private SalaryPayoutService payoutService;
    private TaskScheduler taskScheduler;

    @Override
    public void onEnable() {

        this.economyService = new EconomyService(this);

        if (!economyService.setup()) {
            getLogger().severe("Экономика недоступна - MoonSalary будет отключён!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.configManager = new ConfigManager(this);
        this.groupRegistry = new GroupRegistry(configManager);
        this.messageService = new MessageService(this, configManager, economyService);
        this.afkTracker = new AfkTracker(configManager);
        this.payoutService = new SalaryPayoutService(this, configManager, economyService, messageService);

        this.taskScheduler = new TaskScheduler(this, configManager, groupRegistry,
                afkTracker, messageService, payoutService);

        // Регистрация слушателей
        new EventDispatcher(this).registerEvents(
                new PlayerConnectionListener(afkTracker)
        );

        // Регистрация команд
        new CommandDispatcher(this).registerCommand("salary",
                new SalaryCommand(this, configManager, groupRegistry,
                        afkTracker, messageService, taskScheduler)
        );

        taskScheduler.start();

        if (!configManager.isEnabled()) {
            getLogger().warning("Плагин выключен в config.yml (settings.enabled: false).");
        }

        getLogger().info("MoonSalary успешно запущен! Групп: " + groupRegistry.getRegisteredGroups().size());

    }

    @Override
    public void onDisable() {

        if (taskScheduler != null) {
            taskScheduler.stop();
        }

        if (afkTracker != null) {
            afkTracker.clear();
        }

        if (groupRegistry != null) {
            groupRegistry.clearRegisteredGroups();
        }

        if (economyService != null) {
            economyService.shutdown();
        }

        getLogger().info("MoonSalary остановлен!");

    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GroupRegistry getGroupRegistry() {
        return groupRegistry;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public AfkTracker getAfkTracker() {
        return afkTracker;
    }

    public SalaryPayoutService getPayoutService() {
        return payoutService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }
}
