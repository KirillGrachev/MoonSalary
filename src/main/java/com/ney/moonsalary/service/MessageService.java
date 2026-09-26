package com.ney.moonsalary.service;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.storage.PayoutHistoryService;
import com.ney.moonsalary.util.PlaceholderUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Отправка сообщений, тайтлов и звуков.
 * <p>
 * Все вызовы из основного потока сервера, поэтому флаги «предупреждение
 * уже выведено» - обычные boolean.
 */
public class MessageService {

    private final MoonSalaryConfig configManager;
    private final EconomyService economyService;
    private final ConsoleService consoleService;
    private final PayoutSchedule payoutSchedule;
    private final PayoutHistoryService historyService;

    /** Предупреждение об отсутствии PlaceholderAPI выводится один раз */
    private boolean placeholderWarningSent;

    /** Предупреждение о невалидном паттерне времени выводится один раз */
    private boolean invalidTimePatternWarningSent;

    public MessageService(@NotNull MoonSalaryConfig configManager,
                          @NotNull EconomyService economyService,
                          @NotNull ConsoleService consoleService,
                          @NotNull PayoutSchedule payoutSchedule,
                          @NotNull PayoutHistoryService historyService) {

        this.configManager = configManager;
        this.economyService = economyService;
        this.consoleService = consoleService;
        this.payoutSchedule = payoutSchedule;
        this.historyService = historyService;

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
     * <p>
     * {player} раскрывается в единственном месте - {@code replaceTokens}:
     * при наличии контекста это игрок-контекст, иначе получатель сообщения.
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
        result = PlaceholderUtil.applyServerTime(result);
        warnAboutInvalidTimePattern(result);

        String playerName = context != null ? context.getName() : senderName;

        result = PlaceholderUtil.replaceTokens(result, playerName, formatMoney(money),
                group != null ? group.getName() : null,
                configManager.getSalaryIntervalSeconds(),
                status,
                group != null ? group.getCommands().size() : 0,
                resolveNext(context),
                resolveLast(context));

        return result.replace("{prefix}", configManager.getMessagePrefix());

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
                payoutSchedule.millisUntilNext(context, payoutSchedule.currentTicks()));

    }

    /**
     * Форматирует последнюю выплату игрока из истории: сумма и время.
     * Пока истории нет - статус no_payout из messages.yml.
     *
     * @param context игрок (может быть null)
     * @return строка для {last_payout}
     */
    private @NotNull String resolveLast(@Nullable Player context) {

        if (context == null) {
            return "";
        }

        return historyService.last(context.getUniqueId())
                .map(entry -> PlaceholderUtil.formatMoney(entry.amount())
                        + " (" + formatTime(entry.paidAt()) + ")")
                .orElseGet(configManager::getStatusNoPayout);

    }

    private static @NotNull String formatTime(long epochMillis) {
        return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)
                .format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()));
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

    private void warnAboutInvalidTimePattern(@NotNull String text) {

        if (!PlaceholderUtil.hasUnresolvedServerTime(text)) {
            return;
        }

        if (!invalidTimePatternWarningSent) {
            invalidTimePatternWarningSent = true;
            int start = text.indexOf("{servertime_");
            int end = text.indexOf('}', start);
            consoleService.log(ConsoleMessage.INVALID_TIME_PATTERN,
                    "token", text.substring(start, end != -1 ? end + 1 : text.length()));

        }

    }

    private void warnAboutMissingPlaceholders(@NotNull String text) {

        if (PlaceholderUtil.isSupported() || !PlaceholderUtil.containsPlaceholders(text)) {
            return;
        }

        if (!placeholderWarningSent) {
            placeholderWarningSent = true;
            consoleService.log(ConsoleMessage.PLACEHOLDERS_NO_API);
        }

    }

}
