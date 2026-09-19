package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyServiceTest {

    private MoonSalary plugin;
    private PluginManager pluginManager;
    private ServicesManager servicesManager;
    private EconomyService economyService;

    @BeforeEach
    void setUp() {

        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        servicesManager = mock(ServicesManager.class);

        when(server.getPluginManager()).thenReturn(pluginManager);
        when(server.getServicesManager()).thenReturn(servicesManager);

        plugin = mock(MoonSalary.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());

        this.economyService = new EconomyService(plugin, new ConsoleService(plugin));

    }

    @Test
    @DisplayName("Без Vault экономика не подключается")
    void setupFailsWithoutVault() {

        when(pluginManager.getPlugin("Vault")).thenReturn(null);

        assertFalse(economyService.setup());
        assertFalse(economyService.isAvailable());

    }

    @Test
    @DisplayName("Vault есть, но провайдер экономики не зарегистрирован")
    void setupFailsWithoutEconomyProvider() {

        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        when(servicesManager.getRegistration(Economy.class)).thenReturn(null);

        assertFalse(economyService.setup());
        assertFalse(economyService.isAvailable());

    }

    @Test
    @DisplayName("Выдача денег без экономики безопасна")
    void depositWithoutEconomyIsSafe() {

        Player player = mock(Player.class);

        assertFalse(economyService.deposit(player, 100D));

    }

    @Test
    @DisplayName("Форматирование суммы без экономики возвращает число")
    void formatWithoutEconomyFallsBackToNumber() {

        assertEquals("500.0", economyService.format(500.0D));

    }

    @Test
    @DisplayName("shutdown можно вызывать повторно")
    void shutdownIsIdempotent() {

        economyService.shutdown();
        economyService.shutdown();

        assertFalse(economyService.isAvailable());

    }
}
