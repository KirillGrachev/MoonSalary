package com.ney.moonsalary.listener;

import com.ney.moonsalary.MoonSalary;
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
 * Если Vault выключают на работающем сервере, выплаты ставятся на паузу;
 * при возвращении Vault - возобновляются. Вся логика живёт в {@link MoonSalary},
 * слушатель лишь распознаёт событие нужного плагина.
 */
public class VaultStateListener implements Listener {

    private static final String VAULT_PLUGIN_NAME = "Vault";

    private final MoonSalary plugin;

    public VaultStateListener(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginDisable(@NotNull PluginDisableEvent event) {

        if (isVault(event.getPlugin())) {
            plugin.pausePayouts();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(@NotNull PluginEnableEvent event) {

        if (isVault(event.getPlugin())) {
            plugin.resumePayouts();
        }
    }

    private boolean isVault(@NotNull Plugin plugin) {
        return VAULT_PLUGIN_NAME.equals(plugin.getName());
    }
}
