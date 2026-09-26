package com.ney.moonsalary.listener;

import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.task.TaskScheduler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Следит за входом и выходом игроков,
 * чтобы данные AFK всегда оставались актуальными.
 * <p>
 * События входа/выхода не отменяются, поэтому ignoreCancelled не используется;
 * MONITOR - чтобы реагировать после всех плагинов, меняющих состояние игрока.
 */
public class PlayerConnectionListener implements Listener {

    private final AfkTracker afkTracker;
    private final TaskScheduler taskScheduler;

    public PlayerConnectionListener(@NotNull AfkTracker afkTracker,
                                    @NotNull TaskScheduler taskScheduler) {
        this.afkTracker = afkTracker;
        this.taskScheduler = taskScheduler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        afkTracker.startTracking(event.getPlayer());
        taskScheduler.onPlayerJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        afkTracker.remove(event.getPlayer());
        taskScheduler.onPlayerQuit(event.getPlayer());
    }

}
