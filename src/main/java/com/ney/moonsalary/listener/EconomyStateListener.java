package com.ney.moonsalary.listener;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Следит за доступностью экономики.
 * <p>
 * Два независимых источника событий:
 * <ul>
 *     <li>жизненный цикл плагина Vault - без него Economy-провайдер бесполезен;</li>
 *     <li>(де)регистрация сервиса {@link Economy} в ServicesManager - ловит
 *     перезагрузку самого провайдера (EssentialsX, CMI и т.п.): протухшая ссылка
 *     заменяется свежей, а при unregister выплаты встают на паузу.</li>
 * </ul>
 * Вся логика паузы/возобновления живёт в {@link MoonSalary},
 * слушатель лишь распознаёт события.
 * <p>
 * События жизненного цикла плагинов и сервисов не отменяются,
 * поэтому ignoreCancelled здесь не используется.
 */
public class EconomyStateListener implements Listener {

    private static final String VAULT_PLUGIN_NAME = "Vault";

    private final MoonSalary plugin;

    public EconomyStateListener(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(@NotNull PluginDisableEvent event) {
        if (isVault(event.getPlugin())) {
            plugin.pausePayouts(ConsoleMessage.VAULT_PAUSED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(@NotNull PluginEnableEvent event) {
        if (isVault(event.getPlugin())) {
            plugin.resumePayouts();
        }
    }

    /**
     * Провайдер зарегистрировал новый экземпляр Economy (старт сервера
     * или собственная перезагрузка) - переподключаемся и, если выплаты
     * стояли на паузе, возобновляем их.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceRegister(@NotNull ServiceRegisterEvent event) {
        if (isEconomyService(event.getProvider().getService())) {
            plugin.resumePayouts();
        }
    }

    /**
     * Экземпляр Economy снят с регистрации (провайдер выгружается) -
     * пауза, чтобы не платить через протухший провайдер.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServiceUnregister(@NotNull ServiceUnregisterEvent event) {
        if (isEconomyService(event.getProvider().getService())) {
            plugin.pausePayouts(ConsoleMessage.ECONOMY_PROVIDER_LOST);
        }
    }

    private boolean isVault(@NotNull Plugin plugin) {
        return VAULT_PLUGIN_NAME.equals(plugin.getName());
    }

    private boolean isEconomyService(@NotNull Class<?> service) {
        return Economy.class.equals(service);
    }

}
