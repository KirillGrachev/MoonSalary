package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconomyServiceTest {

    private MoonSalary plugin;
    private PluginManager pluginManager;
    private ServicesManager servicesManager;
    private ConsoleService consoleService;
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
        consoleService = mock(ConsoleService.class);

        this.economyService = new EconomyService(plugin, consoleService);

    }

    @SuppressWarnings("unchecked")
    private Economy hookEconomy() {

        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        Economy economy = mock(Economy.class);
        when(economy.getName()).thenReturn("MockEconomy");
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(economy);
        when(servicesManager.getRegistration(Economy.class)).thenReturn(registration);

        assertTrue(economyService.setup());
        return economy;

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
    @DisplayName("Успешный хук логируется и делегирует формат экономике")
    void setupSucceedsAndDelegatesFormat() {

        Economy economy = hookEconomy();
        when(economy.format(5D)).thenReturn("5$");

        assertTrue(economyService.isAvailable());
        assertEquals("5$", economyService.format(5D));

        verify(consoleService).log(ConsoleMessage.ECONOMY_HOOKED, "provider", "MockEconomy");

    }

    @Test
    @DisplayName("Успешный депозит возвращает true")
    void depositSuccess() {

        Economy economy = hookEconomy();
        Player player = mock(Player.class);
        when(economy.depositPlayer(any(Player.class), anyDouble()))
                .thenReturn(new EconomyResponse(100D, 100D, EconomyResponse.ResponseType.SUCCESS, null));

        assertTrue(economyService.deposit(player, 100D));

    }

    @Test
    @DisplayName("Отказ экономики логируется и возвращает false")
    void depositFailureIsLogged() {

        Economy economy = hookEconomy();
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Ney");
        when(economy.depositPlayer(any(Player.class), anyDouble()))
                .thenReturn(new EconomyResponse(100D, 0D, EconomyResponse.ResponseType.FAILURE, "no money"));

        assertFalse(economyService.deposit(player, 100D));
        verify(consoleService).log(ConsoleMessage.DEPOSIT_FAILED,
                "money", "100.0", "player", "Ney", "reason", "no money");

    }

    @Test
    @DisplayName("Исключение экономики не роняет выплату")
    void depositExceptionIsCaught() {

        Economy economy = hookEconomy();
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Ney");
        when(economy.depositPlayer(any(Player.class), anyDouble()))
                .thenThrow(new RuntimeException("boom"));

        assertFalse(economyService.deposit(player, 100D));

        verify(consoleService).log(ConsoleMessage.DEPOSIT_EXCEPTION,
                "player", "Ney", "reason", "java.lang.RuntimeException: boom");

    }

    @Test
    @DisplayName("Нулевая сумма не доходит до экономики")
    void zeroDepositSkipsEconomy() {

        Economy economy = hookEconomy();
        Player player = mock(Player.class);

        assertFalse(economyService.deposit(player, 0D));
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

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

        hookEconomy();
        economyService.shutdown();
        economyService.shutdown();

        assertFalse(economyService.isAvailable());

    }

    @Test
    @DisplayName("Повторный setup с тем же провайдером не логирует заново")
    void repeatedSetupDoesNotRelog() {

        hookEconomy();

        assertTrue(economyService.setup());
        assertTrue(economyService.setup());

        verify(consoleService, times(1))
                .log(ConsoleMessage.ECONOMY_HOOKED, "provider", "MockEconomy");

    }

    @Test
    @DisplayName("Перерегистрация провайдера подменяет протухшую ссылку")
    void providerSwapIsRehooked() {

        hookEconomy();

        Economy replacement = mock(Economy.class);
        when(replacement.getName()).thenReturn("SecondEconomy");
        when(replacement.format(5D)).thenReturn("5€");
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(replacement);
        when(servicesManager.getRegistration(Economy.class)).thenReturn(registration);

        assertTrue(economyService.setup());
        verify(consoleService).log(ConsoleMessage.ECONOMY_HOOKED, "provider", "SecondEconomy");

        assertEquals("5€", economyService.format(5D));

    }

    @Test
    @DisplayName("Кидающий getName провайдера не роняет подключение")
    void throwingProviderNameIsSurvived() {

        when(pluginManager.getPlugin("Vault")).thenReturn(mock(Plugin.class));
        Economy economy = mock(Economy.class);
        when(economy.getName()).thenThrow(new IllegalStateException("broken provider"));
        RegisteredServiceProvider<Economy> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(economy);
        when(servicesManager.getRegistration(Economy.class)).thenReturn(registration);

        assertTrue(economyService.setup());
        assertTrue(economyService.isAvailable());

    }

}
