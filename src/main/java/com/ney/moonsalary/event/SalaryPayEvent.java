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
 * Вызывается после всех внутренних проверок (включая AFK-блокировку)
 * непосредственно перед депозитом и может быть отменено другими плагинами.
 * Для заблокированных выплат событие не вызывается вовсе.
 * <p>
 * Неотменённое событие означает «депозит будет отправлен в экономику»,
 * но не гарантирует его успех: провайдер экономики может отклонить
 * операцию (нехватка места в хранилище, внутренняя ошибка и т.п.).
 */
public class SalaryPayEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final SalaryGroup group;
    private final double amount;
    private final AfkState afkState;

    private boolean cancelled;

    public SalaryPayEvent(@NotNull Player player,
                          @NotNull SalaryGroup group,
                          double amount,
                          @NotNull AfkState afkState) {

        this.player = player;
        this.group = group;
        this.amount = amount;
        this.afkState = afkState;

    }

    public Player getPlayer() {
        return player;
    }

    public SalaryGroup getGroup() {
        return group;
    }

    public double getAmount() {
        return amount;
    }

    /**
     * Состояние AFK игрока на момент выплаты: ACTIVE или BYPASSED
     * (игрок в AFK, но имеет право обхода).
     *
     * @return состояние AFK
     */
    public @NotNull AfkState getAfkState() {
        return afkState;
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
