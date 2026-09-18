package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.event.SalaryPayEvent;
import com.ney.moonsalary.registry.SalaryGroup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Выдача зарплаты: деньги, команды, сообщения, тайтл и звук.
 */
public class SalaryPayoutService {

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final EconomyService economyService;
    private final MessageService messageService;

    public SalaryPayoutService(@NotNull MoonSalary plugin,
                               @NotNull ConfigManager configManager,
                               @NotNull EconomyService economyService,
                               @NotNull MessageService messageService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.economyService = economyService;
        this.messageService = messageService;
    }

    /**
     * Выдаёт зарплату игроку.
     *
     * @param player   получатель
     * @param group    группа зарплат
     * @param afkState состояние AFK игрока
     * @return true если деньги были выданы
     */
    public boolean payout(@NotNull Player player,
                          @NotNull SalaryGroup group,
                          @NotNull AfkState afkState) {

        double amount = group.getSalary();

        SalaryPayEvent event = new SalaryPayEvent(player, group, afkState, amount);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        if (afkState == AfkState.AFK) {

            notifyAfk(player);
            return false;

        }

        if (!economyService.deposit(player, amount)) {
            return false;
        }

        executeCommands(player, group, amount);
        sendMessages(player, group, amount);
        sendTitle(player, group, amount);

        messageService.playSound(player, configManager.getSalarySound());

        return true;

    }

    /**
     * Выполняет команды группы от имени консоли.
     *
     * @param player игрок
     * @param group  группа зарплат
     * @param amount сумма выплаты
     */
    private void executeCommands(@NotNull Player player,
                                 @NotNull SalaryGroup group,
                                 double amount) {

        if (!configManager.areCommandsEnabled() || !group.hasCommands()) {
            return;
        }

        for (String command : group.getCommands()) {

            String prepared = command
                    .replace("{player}", player.getName())
                    .replace("{money}", messageService.formatMoney(amount))
                    .replace("{group}", group.getName());

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);

        }
    }

    /**
     * Отправляет сообщения группы игроку.
     *
     * @param player игрок
     * @param group  группа зарплат
     * @param amount сумма выплаты
     */
    private void sendMessages(@NotNull Player player,
                              @NotNull SalaryGroup group,
                              double amount) {

        if (!configManager.areMessagesEnabled() || !group.hasMessages()) {
            return;
        }

        messageService.sendFormatted(player, group.getMessages(),
                group, amount, configManager.getStatusOnline());

    }

    /**
     * Показывает тайтл о выплате.
     *
     * @param player игрок
     * @param group  группа зарплат
     * @param amount сумма выплаты
     */
    private void sendTitle(@NotNull Player player,
                           @NotNull SalaryGroup group,
                           double amount) {

        if (!configManager.isTitleEnabled()) {
            return;
        }

        String subtitle = messageService.formatLine(player, player.getName(),
                configManager.getSalarySubtitle(), group, amount, configManager.getStatusOnline());

        messageService.sendTitle(player, configManager.getSalaryTitle(), subtitle);

    }

    /**
     * Повторяет AFK-уведомление, если это разрешено конфигурацией.
     *
     * @param player игрок в AFK
     */
    private void notifyAfk(@NotNull Player player) {

        if (!configManager.areAfkNotificationsEnabled()) {
            return;
        }

        if (!configManager.shouldRepeatAfkMessage()) {
            return;
        }

        messageService.sendAfkWarning(player);
        messageService.playSound(player, configManager.getAfkSound());

    }
}
