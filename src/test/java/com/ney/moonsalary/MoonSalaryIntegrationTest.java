package com.ney.moonsalary;

import com.ney.moonsalary.testutil.FakeVault;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты на MockBukkit: плагин загружается в живой сервер-мок,
 * слушатели работают через настоящий диспетчер событий, планировщик тикает.
 */
class MoonSalaryIntegrationTest {

    private static final long TICKS_PER_SECOND = 20L;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        this.server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Без экономики плагин отключается чисто и не оставляет команду")
    void disablesCleanlyWithoutEconomy() {

        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        server.getScheduler().performTicks(30);

        assertFalse(plugin.isEnabled());

    }

    @Test
    @DisplayName("Игрок, двинувшийся один раз и вставший, не получает зарплату (регрессия stale-снимка)")
    void playerWhoMovedOnceThenStoodGetsNoSalary() {

        Economy economy = hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);

        assertTrue(plugin.isEnabled());
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "group.default", true);

        // 290 секунд стоит, один раз переходит на другую точку...
        server.getScheduler().performTicks(TICKS_PER_SECOND * 290);
        teleportTo(player, 5D, 0D, 5D);

        // ...и стоит до payday: idle давно превысил порог, выплата обязана блокироваться
        server.getScheduler().performTicks(TICKS_PER_SECOND * 3310);

        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("Активный игрок получает зарплату в payday")
    void activePlayerReceivesSalaryOnPayday() {

        Economy economy = hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "group.default", true);

        // Двигается каждые 190 секунд - порог AFK (300) не достигается
        for (int step = 0; step < 18; step++) {
            server.getScheduler().performTicks(TICKS_PER_SECOND * 190);
            teleportTo(player, step % 2 == 0 ? 1D : -1D, 0D, 0D);
        }

        server.getScheduler().performTicks(TICKS_PER_SECOND * 180);

        verify(economy, times(1)).depositPlayer(any(Player.class), anyDouble());

    }

    /**
     * Регистрирует заглушку Vault и мок-экономику до загрузки MoonSalary.
     *
     * @return мок экономики для verify-проверок
     */
    private Economy hookEconomy() {

        Plugin vault = MockBukkit.loadWith(FakeVault.class, "vault-plugin.yml");
        Economy economy = mock(Economy.class);
        when(economy.getName()).thenReturn("MockEconomy");
        when(economy.depositPlayer(any(Player.class), anyDouble()))
                .thenAnswer(invocation -> new EconomyResponse(
                        invocation.getArgument(1, Double.class), 0D,
                        EconomyResponse.ResponseType.SUCCESS, null));
        server.getServicesManager().register(Economy.class, economy, vault, ServicePriority.Normal);

        return economy;

    }

    private void teleportTo(PlayerMock player, double x, double y, double z) {

        Location target = player.getLocation().clone();
        target.add(x, y, z);
        player.teleport(target);

    }

}
