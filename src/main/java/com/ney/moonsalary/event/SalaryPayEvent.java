package com.ney.moonsalary.event;

import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.registry.SalaryGroup;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Событие выдачи зарплаты игроку.
 * <p>
 * Вызывается до фактической выдачи денег и может быть отменено
 * другими плагинами.
 */
public class SalaryPayEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SalaryGroup group;
    private final AfkState afkState;
    private final double amount;

    private boolean cancelled;

    public SalaryPayEvent(@NotNull Player player,
                          @NotNull SalaryGroup group,
                          @NotNull AfkState afkState,
                          double amount) {

        this.player = player;
        this.group = group;
        this.afkState = afkState;
        this.amount = amount;

    }

    public Player getPlayer() {
        return player;
    }

    public SalaryGroup getGroup() {
        return group;
    }

    public AfkState getAfkState() {
        return afkState;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
