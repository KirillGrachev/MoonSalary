package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.storage.PayoutEntry;
import com.ney.moonsalary.storage.PayoutOutcome;
import com.ney.moonsalary.storage.PayoutSource;
import com.ney.moonsalary.event.SalaryPayEvent;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.testutil.FakeVault;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalaryPayoutServiceTest {

    private static final List<String> CAPTURED = new ArrayList<>();

    private ServerMock server;
    private Economy economy;
    private MoonSalary plugin;

    @BeforeEach
    void setUp() {

        CAPTURED.clear();
        server = MockBukkit.mock();

        Plugin vault = MockBukkit.loadWith(FakeVault.class, "vault-plugin.yml");
        economy = mock(Economy.class);

        when(economy.getName()).thenReturn("MockEconomy");
        when(economy.depositPlayer(any(Player.class), anyDouble()))
                .thenAnswer(invocation -> new EconomyResponse(
                        invocation.getArgument(1, Double.class), 0D,
                        EconomyResponse.ResponseType.SUCCESS, null));

        server.getServicesManager().register(Economy.class, economy, vault, ServicePriority.Normal);
        plugin = MockBukkit.load(MoonSalary.class);
        server.getCommandMap().register("cap", new Command("cap") {

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                CAPTURED.add(label + " " + String.join(" ", args));
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return List.of();
            }

        });

    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private SalaryGroup group(List<String> messages, List<String> commands) {
        return new SalaryGroup(new SalaryGroupSettings("g", 100D, 0, messages, commands));
    }

    @Test
    @DisplayName("Отмена SalaryPayEvent отменяет выплату")
    void cancelledEventBlocksPayout() {

        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSalary(SalaryPayEvent event) {
                event.setCancelled(true);
            }
        }, plugin);

        PlayerMock player = server.addPlayer();

        assertFalse(plugin.getServices().getPayoutService()
                .payout(player, group(List.of("paid"), List.of()), AfkState.ACTIVE));
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

        assertNull(player.nextMessage());

    }

    @Test
    @DisplayName("AFK без настроенного blocked_afk блокирует тихо")
    void afkBlockedSilentlyByDefault() {

        PlayerMock player = server.addPlayer();

        assertFalse(plugin.getServices().getPayoutService()
                .payout(player, group(List.of("paid"), List.of()), AfkState.AFK));
        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

        assertNull(player.nextMessage());

    }

    @Test
    @DisplayName("AFK с настроенным blocked_afk объясняет причину")
    void afkBlockedSendsConfiguredMessage() throws IOException {

        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                messages:
                  on_salary:
                    blocked_afk: 'AFK!'
                """);

        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                """);

        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        PlayerMock player = server.addPlayer();

        assertFalse(plugin.getServices().getPayoutService()
                .payout(player, group(List.of("paid"), List.of()), AfkState.AFK));
        assertEquals("AFK!", player.nextMessage());

        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("Успешная выдача: деньги, сообщения и команды группы")
    void successPaysMessagesAndRunsCommands() {

        PlayerMock player = server.addPlayer();
        when(player.getName()).thenAnswer(invocation -> ((PlayerMock) player).getName());

        assertTrue(plugin.getServices().getPayoutService()
                .payout(player, group(List.of("got {money}"), List.of("cap {player}")), AfkState.ACTIVE));

        verify(economy).depositPlayer(player, 100D);

        assertTrue(drain(player).stream().anyMatch(message -> message.contains("got 100")));
        assertEquals(1, CAPTURED.size());
        assertTrue(CAPTURED.get(0).startsWith("cap "));

    }

    @Test
    @DisplayName("execute_commands: false отключает команды группы")
    void commandsDisabledByConfig() throws IOException {

        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  execute_commands: false
                """);

        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                """);

        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        PlayerMock player = server.addPlayer();

        assertTrue(plugin.getServices().getPayoutService()
                .payout(player, group(List.of(), List.of("cap {player}")), AfkState.ACTIVE));

        assertTrue(CAPTURED.isEmpty());

    }

    @Test
    @DisplayName("AFK-блокированная выплата не вызывает SalaryPayEvent")
    void afkBlockedPayoutDoesNotFireEvent() {

        List<Double> fired = new ArrayList<>();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSalary(SalaryPayEvent event) {
                fired.add(event.getAmount());
            }
        }, plugin);

        PlayerMock player = server.addPlayer();

        assertFalse(plugin.getServices().getPayoutService()
                .payout(player, group(List.of("paid"), List.of()), AfkState.AFK));
        assertTrue(fired.isEmpty());

        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("{money} в командах - plain число, {money_formatted} - формат экономики")
    void commandMoneyTokensStayParsable() throws IOException {

        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  format_money: true
                """);

        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                """);

        when(economy.format(100D)).thenReturn("$100.00");
        server.dispatchCommand(server.getConsoleSender(), "salary reload");

        PlayerMock player = server.addPlayer();

        assertTrue(plugin.getServices().getPayoutService()
                .payout(player, group(List.of(), List.of("cap {money}|{money_formatted}")),
                        AfkState.ACTIVE));
        assertEquals(List.of("cap 100|$100.00"), CAPTURED);

    }

    @Test
    @DisplayName("Исходы выплат попадают в историю: PAID, BLOCKED_AFK, CANCELLED")
    void payoutOutcomesAreRecorded() {

        PlayerMock player = server.addPlayer();
        SalaryGroup group = group(List.of(), List.of());

        plugin.getServices().getPayoutService().payout(player, group, AfkState.ACTIVE);
        PayoutEntry scheduled = plugin.getServices().getHistoryService()
                .last(player.getUniqueId()).orElseThrow();

        assertEquals(PayoutOutcome.PAID, scheduled.outcome());
        assertEquals(PayoutSource.SCHEDULED, scheduled.source());

        plugin.getServices().getPayoutService().payout(player, group, AfkState.AFK);

        assertEquals(PayoutOutcome.BLOCKED_AFK,
                plugin.getServices().getHistoryService().last(player.getUniqueId()).orElseThrow().outcome());

        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onSalary(SalaryPayEvent event) {
                event.setCancelled(true);
            }
        }, plugin);

        plugin.getServices().getPayoutService().payout(player, group, AfkState.ACTIVE);

        assertEquals(PayoutOutcome.CANCELLED,
                plugin.getServices().getHistoryService().last(player.getUniqueId()).orElseThrow().outcome());

    }

    @Test
    @DisplayName("Кидающая команда не обрывает остальные команды выплаты")
    void failingCommandDoesNotAbortOthers() {

        server.getCommandMap().register("boom", new Command("boom") {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                throw new IllegalStateException("boom");
            }
        });
        PlayerMock player = server.addPlayer();

        assertTrue(plugin.getServices().getPayoutService()
                .payout(player, group(List.of("paid"), List.of("boom", "cap after")),
                        AfkState.ACTIVE));

        assertEquals(List.of("cap after"), CAPTURED);
        assertTrue(drain(player).stream().anyMatch(message -> message.contains("paid")));

    }

    private List<String> drain(PlayerMock player) {

        List<String> messages = new ArrayList<>();
        String message;

        while ((message = player.nextMessage()) != null) {
            messages.add(message);
        }

        return messages;

    }

}
