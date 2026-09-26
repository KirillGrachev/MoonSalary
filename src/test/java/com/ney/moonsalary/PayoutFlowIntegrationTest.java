package com.ney.moonsalary;

import com.ney.moonsalary.testutil.FakeVault;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Интеграционные тесты payout-флоу на MockBukkit: живой диспетчер событий,
 * command map и тикающий планировщик.
 */
class PayoutFlowIntegrationTest {

    private static final long TPS = 20L;

    private ServerMock server;
    private Plugin vault;
    private Economy economy;

    @BeforeEach
    void setUp() {
        this.server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private void hookEconomy() {

        this.vault = MockBukkit.loadWith(FakeVault.class, "vault-plugin.yml");
        this.economy = mock(Economy.class);
        when(economy.getName()).thenReturn("MockEconomy");
        when(economy.depositPlayer(any(Player.class), anyDouble()))
                .thenAnswer(invocation -> new EconomyResponse(
                        invocation.getArgument(1, Double.class), 0D,
                        EconomyResponse.ResponseType.SUCCESS, null));
        server.getServicesManager().register(Economy.class, economy, vault, ServicePriority.Normal);

    }

    private MoonSalary loadWithPersonalConfig() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  payout:
                    mode: PERSONAL
                    interval: 5
                    pause_while_afk: false # старое поведение: окно сгорает, AFK блокирует
                  afk:
                    check_interval: 4
                    threshold: 27
                messages:
                  on_salary:
                    blocked_afk: 'AFK!'
                """);
        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                    priority: 0
                """);
        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        return plugin;

    }

    private PlayerMock groupedPlayer(MoonSalary plugin) {

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "group.default", true);
        return player;

    }

    private List<String> drain(PlayerMock player) {

        List<String> messages = new ArrayList<>();
        String message;

        while ((message = player.nextMessage()) != null) {
            messages.add(message);
        }

        return messages;

    }

    @Test
    @DisplayName("PERSONAL: окно выплачивается и продляется")
    void personalWindowPaysAndAdvances() throws IOException {

        MoonSalary plugin = loadWithPersonalConfig();
        groupedPlayer(plugin);
        server.getScheduler().performTicks(TPS * 5 + 1);

        verify(economy, times(1)).depositPlayer(any(Player.class), anyDouble());

        server.getScheduler().performTicks(TPS * 5);

        verify(economy, times(2)).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("PERSONAL: AFK после порога блокирует окно и шлёт blocked_afk")
    void afkBlocksWindowAndSendsBlockedMessage() throws IOException {

        MoonSalary plugin = loadWithPersonalConfig();
        PlayerMock player = groupedPlayer(plugin);

        // окна 5..25 сек выплачиваются (метка AFK ставится на 28 сек),
        // окна 30 и 35 сек блокируются с сообщением blocked_afk
        server.getScheduler().performTicks(TPS * 36 + 1);

        verify(economy, times(5)).depositPlayer(any(Player.class), anyDouble());

        assertEquals(2, drain(player).stream().filter(message -> message.equals("AFK!")).count());

    }

    @Test
    @DisplayName("Vault выключен - пауза, включен - возобновление")
    void pauseAndResumeOnVaultLifecycle() {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);

        assertTrue(plugin.isEnabled());
        PlayerMock player = groupedPlayer(plugin);
        player.addAttachment(plugin, "moonsalary.bypass.afk", true);
        server.getPluginManager().callEvent(new PluginDisableEvent(vault));
        server.getScheduler().performTicks(TPS * 3600 + 1);

        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

        server.getPluginManager().callEvent(new PluginEnableEvent(vault));
        server.getScheduler().performTicks(TPS * 3600 + 1);

        verify(economy, times(1)).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("Команды /salary отвечают по ролям")
    void commandFlowsByRole() {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        PlayerMock admin = groupedPlayer(plugin);
        admin.setOp(true);
        admin.performCommand("salary");

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("Your salary")));
        admin.performCommand("salary list");

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("Salary groups")));
        admin.performCommand("salary reload");

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("reloaded successfully")));
        admin.performCommand("salary wat");

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("Usage")));
        admin.performCommand("salary info Ghost");

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("not found")));
        PlayerMock player = groupedPlayer(plugin);
        player.performCommand("salary reload");

        assertTrue(drain(player).stream().anyMatch(message -> message.contains("permission")));
        player.performCommand("salary list");

        assertTrue(drain(player).stream().anyMatch(message -> message.contains("permission")));

    }

    @Test
    @DisplayName("Без экономики плагин отключается и убирает команду из command map")
    void disablesCleanlyAndUnregistersCommand() {

        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        server.getScheduler().performTicks(30);

        assertFalse(plugin.isEnabled());

        // Команда и алиасы вычищены из knownCommands - именно по этой карте
        // Paper перестраивает Brigadier-дерево при syncCommands
        Map<String, ?> knownCommands = server.getCommandMap().getKnownCommands();

        assertFalse(knownCommands.containsKey("salary"));
        assertFalse(knownCommands.containsKey("msalary"));
        assertFalse(knownCommands.containsKey("zp"));

    }

    @Test
    @DisplayName("Окно PERSONAL сохраняется в payouts.yml")
    void personalWindowPersistedInYamlStorage() throws IOException {

        MoonSalary plugin = loadWithPersonalConfig();
        PlayerMock player = groupedPlayer(plugin);
        server.getScheduler().performTicks(2);

        String stored = Files.readString(
                plugin.getDataFolder().toPath().resolve("payouts.yml"));

        assertTrue(stored.contains(player.getUniqueId().toString()));
        assertTrue(stored.contains("next_payout_at"));

    }

    @Test
    @DisplayName("История выплат пишется в хранилище с исходом PAID")
    void payoutHistoryIsRecorded() throws IOException {

        MoonSalary plugin = loadWithPersonalConfig();
        groupedPlayer(plugin);
        server.getScheduler().performTicks(TPS * 5 + 1);

        plugin.getServices().getHistoryService().flush();

        String stored = Files.readString(
                plugin.getDataFolder().toPath().resolve("payouts.yml"));

        assertTrue(stored.contains("outcome: PAID"));

    }

    @Test
    @DisplayName("info <игрок>: без права - явный отказ, а не молчаливое своё инфо")
    void infoOtherDeniedWithoutPermission() {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        PlayerMock target = groupedPlayer(plugin);
        PlayerMock viewer = groupedPlayer(plugin);

        viewer.performCommand("salary info " + target.getName());

        List<String> messages = drain(viewer);

        assertTrue(messages.stream().anyMatch(message -> message.contains("permission")));
        assertFalse(messages.stream().anyMatch(message -> message.contains("Salary of")));

    }

    @Test
    @DisplayName("info <игрок>: при выключенной permission-системе доступно всем")
    void infoOtherWorksWithPermissionsDisabled() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  permissions:
                    enabled: false
                messages:
                  command:
                    info:
                      other:
                        - "Salary of {player}"
                """);
        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                    priority: 0
                """);
        server.dispatchCommand(server.getConsoleSender(), "salary reload");

        PlayerMock target = groupedPlayer(plugin);
        PlayerMock viewer = groupedPlayer(plugin);
        viewer.performCommand("salary info " + target.getName());

        assertTrue(drain(viewer).stream()
                .anyMatch(message -> message.contains("Salary of " + target.getName())));

    }

    @Test
    @DisplayName("PERSONAL: время в AFK не приближает выплату, после возврата окно доходит")
    void afkTimeDoesNotCountTowardsPersonalWindow() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  payout:
                    mode: PERSONAL
                    interval: 5
                    pause_while_afk: true
                  afk:
                    check_interval: 1
                    threshold: 3
                messages:
                  on_salary:
                    blocked_afk: 'AFK!'
                """);
        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                    priority: 0
                """);
        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        PlayerMock player = groupedPlayer(plugin);

        // стоим мимо исходного дедлайна (5 сек): выплаты и blocked-сообщений нет
        server.getScheduler().performTicks(TPS * 15);

        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

        assertTrue(drain(player).stream().noneMatch(message -> message.equals("AFK!")));

        // возвращаемся: окно докручивается и выплачивается
        teleportTo(player, 3D, 0D, 3D);
        server.getScheduler().performTicks(TPS * 8);

        verify(economy, times(1)).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("PERSONAL: цепочка пробуждений переживает кидающую команду")
    void personalChainSurvivesFailingCommand() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        registerThrowingCommand();
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  payout:
                    mode: PERSONAL
                    interval: 5
                """);
        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                    priority: 0
                    commands:
                      - 'boom'
                """);
        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        groupedPlayer(plugin);

        server.getScheduler().performTicks(TPS * 5 + 1);

        verify(economy, times(1)).depositPlayer(any(Player.class), anyDouble());

        // второе окно: если бы эстафета планировщика оборвалась, выплаты бы не было
        server.getScheduler().performTicks(TPS * 5 + 1);

        verify(economy, times(2)).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("GLOBAL: кидающая команда одного игрока не мешает выплате другому")
    void globalIsolatesPlayerFailures() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        registerThrowingCommand();
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), """
                settings:
                  payout:
                    interval: 5
                """);
        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                    priority: 0
                    commands:
                      - 'boom'
                """);
        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        groupedPlayer(plugin);
        groupedPlayer(plugin);

        server.getScheduler().performTicks(TPS * 5 + 1);

        verify(economy, times(2)).depositPlayer(any(Player.class), anyDouble());

    }

    @Test
    @DisplayName("give: админ вручную выдаёт зарплату выбранной группы")
    void givePaysSelectedGroup() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        Files.writeString(plugin.getDataFolder().toPath().resolve("groups.yml"), """
                groups:
                  default:
                    salary: 100
                    priority: 0
                  vip:
                    salary: 250
                    priority: 1
                """);
        server.dispatchCommand(server.getConsoleSender(), "salary reload");
        PlayerMock admin = groupedPlayer(plugin);
        admin.setOp(true);
        PlayerMock target = groupedPlayer(plugin);

        admin.performCommand("salary give " + target.getName() + " vip");

        verify(economy).depositPlayer(target, 250D);

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("Paid")));

        // ручная выплата - полноценная запись истории с источником MANUAL
        plugin.getServices().getHistoryService().flush();
        String stored = Files.readString(
                plugin.getDataFolder().toPath().resolve("payouts.yml"));

        assertTrue(stored.contains("source: MANUAL"));
        assertTrue(stored.contains("outcome: PAID"));

    }

    @Test
    @DisplayName("give: без права - отказ, неизвестная группа - сообщение")
    void giveDeniesWithoutPermissionAndUnknownGroup() throws IOException {

        hookEconomy();
        MoonSalary plugin = MockBukkit.load(MoonSalary.class);
        PlayerMock player = groupedPlayer(plugin);
        PlayerMock target = groupedPlayer(plugin);
        PlayerMock admin = groupedPlayer(plugin);
        admin.setOp(true);

        player.performCommand("salary give " + target.getName() + " default");

        assertTrue(drain(player).stream().anyMatch(message -> message.contains("permission")));

        admin.performCommand("salary give " + target.getName() + " nosuchgroup");

        assertTrue(drain(admin).stream().anyMatch(message -> message.contains("Unknown group")));

        verify(economy, never()).depositPlayer(any(Player.class), anyDouble());

    }

    private void teleportTo(PlayerMock player, double x, double y, double z) {
        Location target = player.getLocation().clone();
        target.add(x, y, z);
        player.teleport(target);
    }

    /**
     * Регистрирует команду, которая всегда кидает исключение:
     * имитация чужего плагина со сломанным обработчиком.
     */
    private void registerThrowingCommand() {

        server.getCommandMap().register("boom", new Command("boom") {

            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                throw new IllegalStateException("boom");
            }

        });

    }

}
