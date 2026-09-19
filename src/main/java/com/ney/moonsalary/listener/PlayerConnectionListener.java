package com.ney.moonsalary.listener;

import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.PayoutSchedule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Следит за входом и выходом игроков,
 * чтобы данные AFK всегда оставались актуальными.
 */
public class PlayerConnectionListener implements Listener {

    private final AfkTracker afkTracker;
    private final PayoutSchedule payoutSchedule;

    public PlayerConnectionListener(@NotNull AfkTracker afkTracker,
                                    @NotNull PayoutSchedule payoutSchedule) {
        this.afkTracker = afkTracker;
        this.payoutSchedule = payoutSchedule;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {

        afkTracker.startTracking(event.getPlayer());
        payoutSchedule.track(event.getPlayer());

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {

        afkTracker.remove(event.getPlayer());
        payoutSchedule.remove(event.getPlayer());

    }
}
