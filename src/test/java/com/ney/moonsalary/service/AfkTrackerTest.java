package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.AfkState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AfkTrackerTest {

    private final UUID playerId = UUID.randomUUID();

    private Player mockPlayer() {

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        return player;

    }

    @Test
    @DisplayName("При выключенной защите игрок всегда активен")
    void afkDisabledMeansActive() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isAfkEnabled()).thenReturn(false);

        Player player = mockPlayer();
        AfkTracker tracker = new AfkTracker(configManager);
        tracker.markAfk(player);

        assertEquals(AfkState.ACTIVE, tracker.resolveState(player));

    }

    @Test
    @DisplayName("Отмеченный игрок без права обхода считается AFK")
    void markedPlayerIsAfk() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isAfkEnabled()).thenReturn(true);
        when(configManager.arePermissionsEnabled()).thenReturn(true);
        when(configManager.getPermissionBypassAfk()).thenReturn("moon_salary.bypass.afk");

        Player player = mockPlayer();
        when(player.hasPermission("moon_salary.bypass.afk")).thenReturn(false);

        AfkTracker tracker = new AfkTracker(configManager);
        tracker.markAfk(player);

        assertTrue(tracker.isAfk(player));
        assertEquals(AfkState.AFK, tracker.resolveState(player));

    }

    @Test
    @DisplayName("Право обхода позволяет получать зарплату в AFK")
    void bypassPermissionAllowsPayout() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isAfkEnabled()).thenReturn(true);
        when(configManager.arePermissionsEnabled()).thenReturn(true);
        when(configManager.getPermissionBypassAfk()).thenReturn("moon_salary.bypass.afk");

        Player player = mockPlayer();
        when(player.hasPermission("moon_salary.bypass.afk")).thenReturn(true);

        AfkTracker tracker = new AfkTracker(configManager);
        tracker.markAfk(player);

        assertFalse(tracker.isAfk(player));
        assertEquals(AfkState.BYPASSED, tracker.resolveState(player));

    }

    @Test
    @DisplayName("Неотмеченный игрок активен")
    void unmarkedPlayerIsActive() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isAfkEnabled()).thenReturn(true);

        Player player = mockPlayer();
        AfkTracker tracker = new AfkTracker(configManager);

        assertEquals(AfkState.ACTIVE, tracker.resolveState(player));

    }

    @Test
    @DisplayName("Трекинг позиций работает отдельно для каждого игрока")
    void tracksPlayersIndependently() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isAfkEnabled()).thenReturn(true);
        when(configManager.isAfkRotationIgnored()).thenReturn(true);

        AfkTracker tracker = new AfkTracker(configManager);

        Location firstLocation = location(10D, 64D, -20D, 90F, 0F);
        Location secondLocation = location(100D, 70D, 100D, 0F, 0F);

        Player first = mockPlayer();
        when(first.getLocation()).thenReturn(firstLocation);

        UUID secondId = UUID.randomUUID();
        Player second = mock(Player.class);
        when(second.getUniqueId()).thenReturn(secondId);
        when(second.getLocation()).thenReturn(secondLocation);

        tracker.startTracking(first);
        tracker.startTracking(second);

        assertFalse(tracker.hasMoved(first));
        assertFalse(tracker.hasMoved(second));

        tracker.markAfk(first);

        assertTrue(tracker.isMarked(first));
        assertFalse(tracker.isMarked(second));

        tracker.remove(first);

        assertFalse(tracker.isMarked(first));
        assertFalse(tracker.isMarked(second));

    }

    @Test
    @DisplayName("Поворот головы не считается движением при ignore_rotation")
    void rotationIsIgnoredWhenConfigured() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isAfkRotationIgnored()).thenReturn(true);

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        Player player = mockPlayer();
        when(player.getLocation()).thenReturn(new Location(world, 10D, 64D, -20D, 90F, 0F));

        AfkTracker tracker = new AfkTracker(configManager);
        tracker.startTracking(player);

        when(player.getLocation()).thenReturn(new Location(world, 10D, 64D, -20D, -35F, 42F));
        assertFalse(tracker.hasMoved(player));

        when(player.getLocation()).thenReturn(new Location(world, 10.5D, 64D, -20D, -35F, 42F));
        assertTrue(tracker.hasMoved(player));

    }

    private Location location(double x, double y, double z, float yaw, float pitch) {

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        return new Location(world, x, y, z, yaw, pitch);

    }
}