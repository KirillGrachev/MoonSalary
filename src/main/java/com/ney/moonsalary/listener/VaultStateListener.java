package com.ney.moonsalary.listener;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.service.EconomyService;
import com.ney.moonsalary.task.TaskScheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Следит за жизненным циклом Vault.
 * <p>
 * Если Vault выключают на работающем сервере (reload плагинов, /pl disable),
 * выплаты ставятся на паузу без исключений; при возвращении Vault - возобновляются.
 */
public class VaultStateListener implements Listener {

    private static final String VAULT_PLUGIN_NAME = "Vault";

    private final MoonSalary plugin;
    private final EconomyService economyService;
    private final TaskScheduler taskScheduler;

    public VaultStateListener(@NotNull MoonSalary plugin,
                              @NotNull EconomyService economyService,
                              @NotNull TaskScheduler taskScheduler) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.taskScheduler = taskScheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginDisable(@NotNull PluginDisableEvent event) {

        if (!isVault(event.getPlugin()) || !economyService.isAvailable()) {
            return;
        }

        taskScheduler.stop();
        economyService.shutdown();

        plugin.getLogger().warning("Vault выключен - выдача зарплат приостановлена.");

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(@NotNull PluginEnableEvent event) {

        if (!isVault(event.getPlugin()) || economyService.isAvailable()) {
            return;
        }

        if (economyService.setup()) {

            taskScheduler.start();
            plugin.getLogger().info("Vault снова доступен - выдача зарплат возобновлена.");

        }
    }

    private boolean isVault(@NotNull Plugin plugin) {
        return VAULT_PLUGIN_NAME.equals(plugin.getName());
    }
}
