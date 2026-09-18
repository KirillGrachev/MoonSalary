package com.ney.moonsalary.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Событие изменения AFK-состояния игрока.
 */
public class PlayerAfkEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final boolean afk;

    public PlayerAfkEvent(@NotNull Player player, boolean afk) {

        this.player = player;
        this.afk = afk;

    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Показывает новое состояние игрока.
     *
     * @return true если игрок ушёл в AFK, false если вернулся
     */
    public boolean isAfk() {
        return afk;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
