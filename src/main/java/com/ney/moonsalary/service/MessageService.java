package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.util.PlaceholderUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Отправка сообщений, тайтлов и звуков.
 */
public class MessageService {

    private final ConfigManager configManager;
    private final EconomyService economyService;
    private final ConsoleService consoleService;
    private final PayoutSchedule payoutSchedule;

    /** Предупреждение об отсутствии PlaceholderAPI выводится один раз */
    private final AtomicBoolean placeholderWarningSent = new AtomicBoolean(false);

    public MessageService(@NotNull ConfigManager configManager,
                          @NotNull EconomyService economyService,
                          @NotNull ConsoleService consoleService,
                          @NotNull PayoutSchedule payoutSchedule) {
        this.configManager = configManager;
        this.economyService = economyService;
        this.consoleService = consoleService;
        this.payoutSchedule = payoutSchedule;
    }

    /**
     * Отправляет игроку сообщения группы с префиксом и плейсхолдерами.
     *
     * @param player   получатель
     * @param messages строки из конфигурации
     * @param group    группа зарплат (может быть null)
     * @param money    сумма выплаты
     * @param status   статус игрока
     */
    public void sendFormatted(@NotNull Player player,
                              @NotNull List<String> messages,
                              @Nullable SalaryGroup group,
                              double money,
                              @NotNull String status) {

        sendFormatted(player, player, messages, group, money, status);

    }

    /**
     * Отправляет сообщения любому получателю (игрок или консоль).
     *
     * @param sender   получатель сообщения
     * @param context  игрок, от лица которого берутся плейсхолдеры
     * @param messages строки из конфигурации
     * @param group    группа зарплат (может быть null)
     * @param money    сумма выплаты
     * @param status   статус игрока
     */
    public void sendFormatted(@NotNull CommandSender sender,
                              @Nullable Player context,
                              @NotNull List<String> messages,
                              @Nullable SalaryGroup group,
                              double money,
                              @NotNull String status) {

        for (String message : messages) {
            sender.sendMessage(formatLine(context, sender.getName(), message, group, money, status));
        }
    }

    /**
     * Отправляет получателю одну строку с плейсхолдерами (включая {prefix}).
     *
     * @param sender получатель
     * @param text   строка из конфигурации
     */
    public void sendLine(@NotNull CommandSender sender, @NotNull String text) {

        String formatted = formatLine(null, sender.getName(), text, null, 0D, "");

        if (!formatted.isEmpty()) {
            sender.sendMessage(formatted);
        }
    }

    /**
     * Форматирует одну строку: PlaceholderAPI + внутренние плейсхолдеры.
     *
     * @param context    игрок для плейсхолдеров PlaceholderAPI (может быть null)
     * @param senderName имя получателя сообщения
     * @param text       исходная строка
     * @param group      группа зарплат (может быть null)
     * @param money      сумма выплаты
     * @param status     статус игрока
     * @return готовая к отправке строка
     */
    public @NotNull String formatLine(@Nullable Player context,
                                      @NotNull String senderName,
                                      @NotNull String text,
                                      @Nullable SalaryGroup group,
                                      double money,
                                      @NotNull String status) {

        String result = PlaceholderUtil.applyPlaceholders(context, text);

        warnAboutMissingPlaceholders(result);

        result = PlaceholderUtil.replaceTokens(result, context, money,
                group != null ? group.getName() : null,
                configManager.getSalaryIntervalSeconds(),
                status,
                group != null ? group.getCommands().size() : 0,
                resolveNext(context));

        return result
                .replace("{player}", senderName)
                .replace("{prefix}", configManager.getMessagePrefix());

    }

    /**
     * Показывает игроку тайтл.
     *
     * @param player   получатель
     * @param title    заголовок (пустая строка - не показывать)
     * @param subtitle подзаголовок (пустая строка - не показывать)
     */
    public void sendTitle(@NotNull Player player,
                          @Nullable String title,
                          @Nullable String subtitle) {

        String preparedTitle = title != null ? title : "";
        String preparedSubtitle = subtitle != null ? subtitle : "";

        if (preparedTitle.isEmpty() && preparedSubtitle.isEmpty()) {
            return;
        }

        player.sendTitle(preparedTitle, preparedSubtitle,
                configManager.getTitleFadeIn(),
                configManager.getTitleStay(),
                configManager.getTitleFadeOut());

    }

    /**
     * Проигрывает звук игроку.
     *
     * @param player   получатель
     * @param settings настройки звука из конфигурации
     */
    public void playSound(@NotNull Player player, @NotNull SoundSettings settings) {

        if (!settings.isPlayable()) {
            return;
        }

        player.playSound(player.getLocation(), settings.sound(),
                settings.volume(), settings.pitch());

    }

    /**
     * Форматирует сумму с учётом настройки format_money.
     *
     * @param money сумма
     * @return строковое представление суммы
     */
    public @NotNull String formatMoney(double money) {

        if (configManager.isMoneyFormattingEnabled()) {
            return economyService.format(money);
        }

        return PlaceholderUtil.formatMoney(money);

    }

    /**
     * Склеивает несколько строк в одну (используется для тайтлов).
     *
     * @param context    игрок для плейсхолдеров
     * @param viewer     получатель сообщения
     * @param messages   строки из конфигурации
     * @param status     статус игрока
     * @return одна готовая строка
     */
    private @NotNull String joinLines(@Nullable Player context,
                                      @NotNull Player viewer,
                                      @NotNull List<String> messages,
                                      @NotNull String status) {

        return String.join(" ", messages.stream()
                .filter(line -> !line.isEmpty())
                .map(line -> formatLine(context, viewer.getName(), line, null, 0D, status))
                .toList());

    }

    /**
     * Считает обратный отсчёт до следующей выплаты игрока.
     *
     * @param context игрок (может быть null)
     * @return форматированная длительность или пустая строка
     */
    private @NotNull String resolveNext(@Nullable Player context) {

        if (context == null) {
            return "";
        }

        return PlaceholderUtil.formatDuration(
                payoutSchedule.millisUntilNext(context, System.currentTimeMillis()));

    }

    private void warnAboutMissingPlaceholders(@NotNull String text) {

        if (PlaceholderUtil.isSupported() || !PlaceholderUtil.containsPlaceholders(text)) {
            return;
        }

        if (placeholderWarningSent.compareAndSet(false, true)) {
            consoleService.log(ConsoleMessage.PLACEHOLDERS_NO_API);
        }
    }
}
