package com.ney.moonsalary.service;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.event.SalaryPayEvent;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.storage.PayoutHistoryService;
import com.ney.moonsalary.storage.PayoutOutcome;
import com.ney.moonsalary.storage.PayoutSource;
import com.ney.moonsalary.util.PlaceholderUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Выдача зарплаты: деньги, команды, сообщения, тайтл и звук.
 * <p>
 * Порядок шагов фиксирован: сначала все внутренние проверки (AFK), затем
 * событие для внешних плагинов, затем депозит. {@link SalaryPayEvent}
 * вызывается только для выплат, которые действительно могут состояться.
 */
public class SalaryPayoutService {

    private final MoonSalaryConfig configManager;
    private final EconomyService economyService;
    private final MessageService messageService;
    private final ConsoleService consoleService;
    private final PayoutHistoryService historyService;

    public SalaryPayoutService(@NotNull MoonSalaryConfig configManager,
                               @NotNull EconomyService economyService,
                               @NotNull MessageService messageService,
                               @NotNull ConsoleService consoleService,
                               @NotNull PayoutHistoryService historyService) {

        this.configManager = configManager;
        this.economyService = economyService;
        this.messageService = messageService;
        this.consoleService = consoleService;
        this.historyService = historyService;

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
        return payout(player, group, afkState, PayoutSource.SCHEDULED);
    }

    /**
     * Выдаёт зарплату игрока с указанием источника (расписание или ручная команда).
     *
     * @param player   получатель
     * @param group    группа зарплат
     * @param afkState состояние AFK игрока
     * @param source   источник выплаты для истории
     * @return true если деньги были выданы
     */
    public boolean payout(@NotNull Player player,
                          @NotNull SalaryGroup group,
                          @NotNull AfkState afkState,
                          @NotNull PayoutSource source) {

        double amount = group.getSalary();

        // AFK-блокировка проверяется до события: слушатели SalaryPayEvent
        // не должны видеть выплаты, которые гарантированно не состоятся
        if (afkState == AfkState.AFK) {
            historyService.record(player, group, amount, PayoutOutcome.BLOCKED_AFK, source);
            sendBlockedMessage(player, group, amount);
            return false;
        }

        SalaryPayEvent event = new SalaryPayEvent(player, group, amount, afkState);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            historyService.record(player, group, amount, PayoutOutcome.CANCELLED, source);
            return false;
        }

        if (!economyService.deposit(player, amount)) {
            historyService.record(player, group, amount, PayoutOutcome.FAILED, source);
            return false;
        }

        historyService.record(player, group, amount, PayoutOutcome.PAID, source);

        executeCommands(player, group, amount);
        sendMessages(player, group, amount);
        sendTitle(player, group, amount);

        messageService.playSound(player, configManager.getSalarySound());
        return true;

    }

    /**
     * Выполняет команды группы от имени консоли.
     * <p>
     * Каждая команда изолирована try/catch: кидающий плагин-владелец команды
     * не может оборвать ни остальные команды, ни сообщения/тайтл/звук выплаты.
     * <p>
     * {money} в командах - всегда обычное число без символа валюты:
     * форматирование из format_money ломает парсинг аргументов чужих команд.
     * Для отображения есть {money_formatted}.
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
                    .replace("{money_formatted}", messageService.formatMoney(amount))
                    .replace("{money}", PlaceholderUtil.formatMoney(amount))
                    .replace("{group}", group.getName());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), prepared);
            } catch (RuntimeException exception) {
                consoleService.log(ConsoleMessage.COMMAND_ERROR,
                        "command", prepared,
                        "player", player.getName(),
                        "reason", String.valueOf(exception));
            }

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
     * И заголовок, и подзаголовок проходят через форматирование:
     * плейсхолдеры работают в обоих полях одинаково.
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

        String status = configManager.getStatusOnline();
        String title = messageService.formatLine(player, player.getName(),
                configManager.getSalaryTitle(), group, amount, status);
        String subtitle = messageService.formatLine(player, player.getName(),
                configManager.getSalarySubtitle(), group, amount, status);

        messageService.sendTitle(player, title, subtitle);

    }

    /**
     * Отправляет сообщение о заблокированной выплате.
     * Пустая строка в конфигурации означает полную тишину.
     *
     * @param player игрок в AFK
     * @param group  группа зарплат
     * @param amount сумма, которую игрок не получил
     */
    private void sendBlockedMessage(@NotNull Player player,
                                    @NotNull SalaryGroup group,
                                    double amount) {

        String message = configManager.getBlockedAfkMessage();

        if (message.isEmpty()) {
            return;
        }

        messageService.sendFormatted(player, List.of(message),
                group, amount, configManager.getStatusAfk());

    }

}
