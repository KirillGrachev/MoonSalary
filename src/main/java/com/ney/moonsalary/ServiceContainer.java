package com.ney.moonsalary;

import com.ney.moonsalary.command.CommandDispatcher;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.ConsoleService;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.service.MessageService;
import com.ney.moonsalary.service.PayoutSchedule;
import com.ney.moonsalary.service.SalaryPayoutService;
import com.ney.moonsalary.storage.PayoutHistoryService;
import com.ney.moonsalary.storage.PayoutRepository;
import com.ney.moonsalary.storage.PayoutRepositoryFactory;
import com.ney.moonsalary.task.TaskScheduler;
import com.ney.moonsalary.task.TickClock;
import org.jetbrains.annotations.NotNull;

/**
 * Контейнер компонентов плагина.
 * <p>
 * Порядок создания зафиксирован: каждый сервис получает уже готовые
 * зависимости. Контейнер собирает граф полностью, чтобы ни один компонент
 * не создавал зависимости себе сам и не искал их по чужим геттерам.
 */
public final class ServiceContainer {

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final ConsoleService consoleService;
    private final EconomyService economyService;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final PayoutRepository payoutRepository;
    private final PayoutHistoryService payoutHistoryService;
    private final PayoutSchedule payoutSchedule;
    private final MessageService messageService;
    private final SalaryPayoutService payoutService;
    private final TickClock tickClock;
    private final TaskScheduler taskScheduler;
    private final CommandDispatcher commandDispatcher;

    public ServiceContainer(@NotNull MoonSalary plugin,
                            @NotNull ConfigManager configManager,
                            @NotNull ConsoleService consoleService) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.consoleService = consoleService;

        // Базовый слой
        this.economyService = new EconomyService(plugin, consoleService);
        this.groupRegistry = new GroupRegistry(configManager);
        this.afkTracker = new AfkTracker(configManager);
        this.payoutRepository = new PayoutRepositoryFactory(plugin, configManager).create();
        this.payoutHistoryService = new PayoutHistoryService(configManager, payoutRepository);
        this.tickClock = new TickClock();
        this.payoutSchedule = new PayoutSchedule(configManager, payoutRepository,
                tickClock::current, System::currentTimeMillis);

        // Слой сообщений и выплат
        this.messageService = new MessageService(configManager, economyService,
                consoleService, payoutSchedule, payoutHistoryService);
        this.payoutService = new SalaryPayoutService(configManager, economyService,
                messageService, consoleService, payoutHistoryService);

        // Слой задач и команд
        this.taskScheduler = new TaskScheduler(plugin, configManager, groupRegistry,
                afkTracker, economyService, payoutSchedule, payoutService, consoleService,
                tickClock);
        this.commandDispatcher = new CommandDispatcher(plugin, consoleService);

    }

    public MoonSalary getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public GroupRegistry getGroupRegistry() {
        return groupRegistry;
    }

    public AfkTracker getAfkTracker() {
        return afkTracker;
    }

    public PayoutRepository getPayoutRepository() {
        return payoutRepository;
    }

    public PayoutHistoryService getHistoryService() {
        return payoutHistoryService;
    }

    public PayoutSchedule getPayoutSchedule() {
        return payoutSchedule;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public SalaryPayoutService getPayoutService() {
        return payoutService;
    }

    public TaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public TickClock getTickClock() {
        return tickClock;
    }

    public CommandDispatcher getCommandDispatcher() {
        return commandDispatcher;
    }

}
