package com.ney.moonsalary;

import com.ney.moonsalary.command.CommandDispatcher;
import com.ney.moonsalary.command.SalaryCommand;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.event.EventDispatcher;
import com.ney.moonsalary.listener.PlayerConnectionListener;
import com.ney.moonsalary.listener.VaultStateListener;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.MessageService;
import com.ney.moonsalary.service.SalaryPayoutService;
import com.ney.moonsalary.task.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

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

        try {

            initializeComponents();
            registerListeners();
            registerCommands();

            if (economyService.setup()) {

                startPayouts();

            } else {

                // Экономика может зарегистрироваться позже нас -
                // повторяем попытку на первом тике, когда все плагины уже включены
                getLogger().warning("Экономика не найдена - повторная попытка после завершения запуска сервера.");
                Bukkit.getScheduler().runTask(this, this::retryEconomyHook);

            }

        } catch (Exception exception) {

            getLogger().severe("MoonSalary не смог включиться: " + exception.getMessage());
            getLogger().log(Level.FINE, "Причина сбоя при включении", exception);

            Bukkit.getPluginManager().disablePlugin(this);

        }
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

    /**
     * Создаёт все компоненты плагина.
     */
    private void initializeComponents() {

        this.economyService = new EconomyService(this);
        this.configManager = new ConfigManager(this);
        this.groupRegistry = new GroupRegistry(configManager);
        this.messageService = new MessageService(this, configManager, economyService);
        this.afkTracker = new AfkTracker(configManager);
        this.payoutService = new SalaryPayoutService(this, configManager, economyService, messageService);

        this.taskScheduler = new TaskScheduler(this, configManager, groupRegistry,
                afkTracker, economyService, messageService, payoutService);

    }

    /**
     * Регистрирует слушателей плагина.
     */
    private void registerListeners() {

        new EventDispatcher(this).registerEvents(
                new PlayerConnectionListener(afkTracker),
                new VaultStateListener(this, economyService, taskScheduler)
        );

    }

    /**
     * Регистрирует команды плагина.
     */
    private void registerCommands() {

        new CommandDispatcher(this).registerCommand("salary",
                new SalaryCommand(this, configManager, groupRegistry,
                        afkTracker, messageService, taskScheduler)
        );

    }

    /**
     * Повторяет попытку подключения экономики после полного старта сервера.
     * Если экономики всё ещё нет - плагин выключается одним понятным сообщением,
     * без stacktrace в консоли.
     */
    private void retryEconomyHook() {

        if (economyService.setup()) {

            startPayouts();
            getLogger().info("Экономика найдена - MoonSalary полностью включён.");
            return;

        }

        getLogger().severe("MoonSalary не включён: требуются Vault и плагин экономики "
                + "(EssentialsX, CMI и т.д.). Установите зависимости и перезапустите сервер.");

        Bukkit.getPluginManager().disablePlugin(this);

    }

    /**
     * Запускает циклы выплат и проверки AFK.
     */
    private void startPayouts() {

        taskScheduler.start();

        if (!configManager.isEnabled()) {
            getLogger().warning("Плагин выключен в config.yml (settings.enabled: false).");
        }

        getLogger().info("MoonSalary успешно запущен! Групп: " + groupRegistry.getRegisteredGroups().size());

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
