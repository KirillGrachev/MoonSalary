package com.ney.moonsalary.event;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.registry.SalaryGroup;
import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PluginEventsTest {

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
    @DisplayName("SalaryPayEvent несёт игрока, группу, сумму и AFK-состояние, отменяется")
    void salaryPayEventAccessors() {

        Player player = mock(Player.class);
        SalaryGroup group = new SalaryGroup(new SalaryGroupSettings("g", 50D, 1, List.of(), List.of()));
        SalaryPayEvent event = new SalaryPayEvent(player, group, 50D, AfkState.BYPASSED);

        assertSame(player, event.getPlayer());
        assertSame(group, event.getGroup());
        assertEquals(50D, event.getAmount());
        assertEquals(AfkState.BYPASSED, event.getAfkState());
        assertFalse(event.isCancelled());
        event.setCancelled(true);

        assertTrue(event.isCancelled());
        assertSame(event.getHandlers(), SalaryPayEvent.getHandlerList());

    }

    @Test
    @DisplayName("PlayerAfkEvent несёт игрока и новое состояние")
    void playerAfkEventAccessors() {

        Player player = mock(Player.class);
        PlayerAfkEvent event = new PlayerAfkEvent(player, true);

        assertSame(player, event.getPlayer());
        assertTrue(event.isAfk());
        assertSame(event.getHandlers(), PlayerAfkEvent.getHandlerList());

    }

    @Test
    @DisplayName("EventDispatcher регистрирует слушателей на живом диспетчере")
    void eventDispatcherRegistersListeners() {

        MoonSalary plugin = MockBukkit.load(MoonSalary.class);

        PlayerMock player = server.addPlayer();
        List<Boolean> received = new ArrayList<>();
        new EventDispatcher(plugin).registerEvents(new Listener() {
            @EventHandler
            public void onAfk(PlayerAfkEvent event) {
                received.add(event.isAfk());
            }
        });

        server.getPluginManager().callEvent(new PlayerAfkEvent(player, true));
        assertEquals(List.of(true), received);

    }

}
