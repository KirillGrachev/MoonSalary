package com.ney.moonsalary;

import com.ney.moonsalary.command.SalaryCommand;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.event.EventDispatcher;
import com.ney.moonsalary.listener.EconomyStateListener;
import com.ney.moonsalary.listener.PlayerConnectionListener;
import com.ney.moonsalary.service.ConsoleService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Точка входа плагина.
 * <p>
 * Жизненный цикл выплат описывается двумя флагами:
 * <ul>
 *     <li>{@code commandsRegistered} - команды регистрируются один раз и больше не снимаются
 *     до onDisable (повторная активация их не дублирует);</li>
 *     <li>{@code payoutsActive} - задачи работают, экономика подключена.
 *     Пауза (Vault/провайдер выключены) сбрасывает флаг, возобновление - выставляет.</li>
 * </ul>
 * Оба флага читаются и пишутся только из основного потока сервера,
 * поэтому гонки между retry-тиком и событиями Vault/ServicesManager исключены
 * проверками, а не синхронизацией.
 * <p>
 * Класс не final осознанно: интеграционные тесты (MockBukkit) грузят плагин
 * через прокси-подкласс, а наследование JavaPlugin в продакшене никто не
 * использует - подклассы создаются только тестовой средой.
 */
public class MoonSalary extends JavaPlugin {

    private ConsoleService consoleService;
    private ConfigManager configManager;
    private ServiceContainer services;

    /** Команды зарегистрированы только когда плагин реально работоспособен */
    private boolean commandsRegistered;

    /** Задачи запущены и экономика подключена */
    private boolean payoutsActive;

    @Override
    public void onEnable() {

        this.consoleService = new ConsoleService(this);

        try {

            this.configManager = new ConfigManager(this, consoleService);
            this.services = new ServiceContainer(this, configManager, consoleService);

            registerListeners();

            if (services.getEconomyService().setup()) {
                activate();
                logStartup();
            } else {

                // Экономика может зарегистрироваться позже нас - повторяем попытку
                // на первом тике, когда все плагины уже включены. До активации
                // слушатель ServicesManager также может поднять плагин раньше -
                // retry в этом случае просто выйдет по флагу payoutsActive.
                // Команды до активации не регистрируются вовсе
                consoleService.log(ConsoleMessage.ECONOMY_WAITING);
                Bukkit.getScheduler().runTask(this, this::retryEconomyHook);

            }

        } catch (Exception exception) {

            // Одна понятная строка в консоль + полный стектрейс на SEVERE:
            // причину сбоя должно быть видно без включения debug-логирования
            consoleService.log(ConsoleMessage.STARTUP_FAILED,
                    "reason", String.valueOf(exception));
            getLogger().log(Level.SEVERE, "MoonSalary startup failure details", exception);
            Bukkit.getPluginManager().disablePlugin(this);

        }

    }

    @Override
    public void onDisable() {

        payoutsActive = false;

        if (services == null) {
            return;
        }

        services.getTaskScheduler().stop();
        services.getCommandDispatcher().unregisterCommand("salary");
        services.getAfkTracker().clear();
        services.getPayoutSchedule().clear();
        services.getGroupRegistry().clearRegisteredGroups();
        services.getHistoryService().close();
        services.getPayoutRepository().close();
        services.getEconomyService().shutdown();

        consoleService.log(ConsoleMessage.SHUTDOWN);

    }

    /**
     * Пауза выплат: Vault или провайдер экономики недоступны на работающем сервере.
     * Повторный вызов безопасен: если выплаты уже на паузе, ничего не происходит.
     *
     * @param reason сообщение консоли о причине паузы
     */
    public void pausePayouts(@NotNull ConsoleMessage reason) {

        if (services == null || !payoutsActive) {
            return;
        }

        payoutsActive = false;
        services.getTaskScheduler().stop();
        services.getEconomyService().shutdown();

        consoleService.log(reason);

    }

    /**
     * Возобновление выплат: Vault/провайдер снова доступны.
     * <p>
     * Если плагин уже активен, вызов лишь обновляет ссылку на провайдера
     * внутри {@link com.ney.moonsalary.service.EconomyService#setup()} -
     * задачи не перезапускаются, глобальный якорь payday не сбрасывается.
     */
    public void resumePayouts() {

        if (services == null) {
            return;
        }

        if (!services.getEconomyService().setup()) {
            return;
        }

        if (payoutsActive) {
            return;
        }

        boolean firstActivation = !commandsRegistered;
        activate();

        consoleService.log(firstActivation
                ? ConsoleMessage.ECONOMY_LATE
                : ConsoleMessage.VAULT_RESUMED);

    }

    /**
     * Повторяет попытку подключения экономики после полного старта сервера.
     * Если экономики всё ещё нет - плагин отключается. Команды к этому моменту
     * не зарегистрированы, поэтому «зомби» с CommandException возникнуть не может.
     */
    private void retryEconomyHook() {

        // Слушатель ServicesManager мог активировать плагин раньше этого тика
        if (payoutsActive) {
            return;
        }

        if (services.getEconomyService().setup()) {

            activate();
            logStartup();

            consoleService.log(ConsoleMessage.ECONOMY_LATE);
            return;

        }

        consoleService.log(ConsoleMessage.ECONOMY_MISSING);
        Bukkit.getPluginManager().disablePlugin(this);

    }

    /**
     * Полноценная активация: задачи и команды появляются только здесь,
     * когда наличие экономического провайдера уже подтверждено.
     * Повторный вызов идемпотентен.
     */
    private void activate() {

        services.getTaskScheduler().start();
        services.getHistoryService().start(this);
        payoutsActive = true;

        if (!commandsRegistered) {
            registerCommands();
            this.commandsRegistered = true;
        }

    }

    /**
     * Стартовые сообщения консоли: предупреждение об отключённом плагине
     * и итог запуска с числом групп.
     */
    private void logStartup() {

        if (!configManager.isEnabled()) {
            consoleService.log(ConsoleMessage.DISABLED_BY_CONFIG);
        }

        consoleService.log(ConsoleMessage.STARTUP,
                "groups", String.valueOf(services.getGroupRegistry().getRegisteredGroups().size()));

    }

    /**
     * Регистрирует слушателей плагина.
     */
    private void registerListeners() {
        new EventDispatcher(this).registerEvents(
                new PlayerConnectionListener(services.getAfkTracker(), services.getTaskScheduler()),
                new EconomyStateListener(this)
        );
    }

    /**
     * Регистрирует команды плагина.
     */
    private void registerCommands() {
        services.getCommandDispatcher().registerCommand("salary",
                new SalaryCommand(configManager, services.getGroupRegistry(),
                        services.getAfkTracker(), services.getMessageService(),
                        services.getTaskScheduler(), consoleService,
                        services.getPayoutService())
        );
    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ServiceContainer getServices() {
        return services;
    }

}
