package com.ney.moonsalary.command;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.ConsoleService;
import com.ney.moonsalary.service.MessageService;
import com.ney.moonsalary.service.SalaryPayoutService;
import com.ney.moonsalary.storage.PayoutSource;
import com.ney.moonsalary.task.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Команда /salary - информация о зарплате, список групп и перезагрузка.
 * <p>
 * Все проверки прав идут через единый хелпер, уважающий
 * {@code settings.permissions.enabled}; отказ всегда явный (сообщение),
 * а не молчаливый фолбэк.
 */
public class SalaryCommand implements TabExecutor {

    private static final String ARG_INFO = "info";
    private static final String ARG_RELOAD = "reload";
    private static final String ARG_LIST = "list";
    private static final String ARG_GIVE = "give";

    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final MessageService messageService;
    private final TaskScheduler taskScheduler;
    private final ConsoleService consoleService;
    private final SalaryPayoutService payoutService;

    public SalaryCommand(@NotNull ConfigManager configManager,
                         @NotNull GroupRegistry groupRegistry,
                         @NotNull AfkTracker afkTracker,
                         @NotNull MessageService messageService,
                         @NotNull TaskScheduler taskScheduler,
                         @NotNull ConsoleService consoleService,
                         @NotNull SalaryPayoutService payoutService) {

        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.messageService = messageService;
        this.taskScheduler = taskScheduler;
        this.consoleService = consoleService;
        this.payoutService = payoutService;

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (args.length == 0) {
            sendInfo(sender, sender instanceof Player player ? player : null);
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        switch (subCommand) {

            case ARG_RELOAD -> reload(sender);
            case ARG_LIST -> sendList(sender);
            case ARG_GIVE -> give(sender, args);
            case ARG_INFO -> {
                if (args.length > 1) {

                    // Отдельное право на чужое инфо; отказ - явное сообщение,
                    // а не молчаливый показ собственной зарплаты
                    if (hasPermission(sender, configManager.getPermissionInfo())) {
                        sendInfo(sender, findPlayer(sender, args[1]));
                    }

                } else {
                    sendInfo(sender, sender instanceof Player player ? player : null);
                }
            }

            default -> sendMessage(sender, configManager.getUsageMessage());

        }

        return true;

    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                String @NotNull [] args) {

        if (args.length == 1) {

            List<String> suggestions = new ArrayList<>(List.of(ARG_INFO));

            if (permitted(sender, configManager.getPermissionReload())) {
                suggestions.add(ARG_RELOAD);
            }

            if (permitted(sender, configManager.getPermissionList())) {
                suggestions.add(ARG_LIST);
            }

            if (permitted(sender, configManager.getPermissionGive())) {
                suggestions.add(ARG_GIVE);
            }

            return filter(suggestions, args[0]);

        }

        boolean infoOther = ARG_INFO.equalsIgnoreCase(args[0])
                && permitted(sender, configManager.getPermissionInfo());
        boolean givePlayer = ARG_GIVE.equalsIgnoreCase(args[0])
                && permitted(sender, configManager.getPermissionGive());

        if (args.length == 2 && (infoOther || givePlayer)) {
            return filter(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList(), args[1]);
        }

        if (args.length == 3 && givePlayer) {
            return filter(groupRegistry.getRegisteredGroups().stream()
                    .map(SalaryGroup::getName)
                    .toList(), args[2]);
        }

        return Collections.emptyList();

    }

    /**
     * Показывает информацию о зарплате игрока.
     *
     * @param sender отправитель команды
     * @param target игрок, о котором нужна информация (может быть null)
     */
    private void sendInfo(@NotNull CommandSender sender, @Nullable Player target) {

        if (target == null) {
            sendMessage(sender, configManager.getUsageMessage());
            return;
        }

        SalaryGroup group = groupRegistry.getPlayerGroup(target);
        double salary = group != null ? group.getSalary() : 0D;
        List<String> lines = target.equals(sender)
                ? configManager.getInfoSelfMessage()
                : configManager.getInfoOtherMessage();

        messageService.sendFormatted(sender, target, lines, group, salary,
                resolveStatus(target, group));

    }

    /**
     * Показывает список всех настроенных групп.
     *
     * @param sender отправитель команды
     */
    private void sendList(@NotNull CommandSender sender) {

        if (!hasPermission(sender, configManager.getPermissionList())) {
            return;
        }

        List<SalaryGroup> groups = groupRegistry.getRegisteredGroups();

        if (groups.isEmpty()) {
            sendMessage(sender, configManager.getListEmpty());
            return;
        }

        String header = configManager.getListHeader()
                .replace("{count}", String.valueOf(groups.size()));
        messageService.sendLine(sender, header);

        for (SalaryGroup group : groups) {
            String entry = configManager.getListEntry()
                    .replace("{group}", group.getName())
                    .replace("{money}", messageService.formatMoney(group.getSalary()))
                    .replace("{priority}", String.valueOf(group.getPriority()));
            messageService.sendLine(sender, entry);
        }

    }

    /**
     * Перезагружает конфигурацию, реестр групп и задачи.
     *
     * @param sender отправитель команды
     */
    private void reload(@NotNull CommandSender sender) {

        if (!hasPermission(sender, configManager.getPermissionReload())) {
            return;
        }

        // Единственный источник истины - ConfigManager: он сам перечитывает
        // файл, дублирующий plugin.reloadConfig() не нужен
        configManager.reload();
        groupRegistry.reloadRegistry();
        afkTracker.clear();
        taskScheduler.reschedule();

        consoleService.log(ConsoleMessage.RELOADED,
                "groups", String.valueOf(groupRegistry.getRegisteredGroups().size()));
        sendMessage(sender, configManager.getReloadSuccessMessage());

    }

    /**
     * Ручная выплата зарплаты указанной группы указанному игроку.
     * AFK-состояние не учитывается: это явное админское действие,
     * но SalaryPayEvent по-прежнему позволяет другим плагинам отменить выплату.
     *
     * @param sender отправитель команды
     * @param args   аргументы команды (give <player> <group>)
     */
    private void give(@NotNull CommandSender sender, String @NotNull [] args) {

        if (!hasPermission(sender, configManager.getPermissionGive())) {
            return;
        }

        if (args.length < 3) {
            sendMessage(sender, configManager.getGiveUsageMessage());
            return;
        }

        Player target = findPlayer(sender, args[1]);

        if (target == null) {
            return;
        }

        SalaryGroup group = groupRegistry.getGroup(args[2]);

        if (group == null) {
            sendMessage(sender, configManager.getGiveUnknownGroupMessage()
                    .replace("{group}", args[2]));
            return;
        }

        // Ручная выдача: история помечается MANUAL, персональное окно выплаты
        // при этом не сбрасывается и не продляется - give не влияет на таймер
        boolean paid = payoutService.payout(target, group, AfkState.ACTIVE, PayoutSource.MANUAL);

        String template = paid
                ? configManager.getGiveSuccessMessage()
                : configManager.getGiveFailedMessage();
        sendMessage(sender, template
                .replace("{player}", target.getName())
                .replace("{group}", group.getName())
                .replace("{money}", messageService.formatMoney(group.getSalary())));

    }

    /**
     * Статус игрока для info: группа не найдена / AFK / онлайн.
     * Target всегда онлайн: он либо sender, либо найден getPlayerExact.
     *
     * @param target игрок
     * @param group  группа игрока (может быть null)
     * @return строка статуса из конфигурации
     */
    private @NotNull String resolveStatus(@NotNull Player target, @Nullable SalaryGroup group) {

        if (group == null) {
            return configManager.getStatusNoGroup();
        }

        return afkTracker.isAfk(target)
                ? configManager.getStatusAfk()
                : configManager.getStatusOnline();

    }

    private @Nullable Player findPlayer(@NotNull CommandSender sender, @NotNull String name) {

        Player target = Bukkit.getPlayerExact(name);

        if (target == null) {
            sendMessage(sender, configManager.getUnknownPlayerMessage()
                    .replace("{player}", name));
        }

        return target;

    }

    /**
     * Тихая проверка права: учитывает глобальный выключатель permission-системы,
     * но не отправляет сообщений (для tab-complete).
     *
     * @param sender     отправитель
     * @param permission право
     * @return true если право действует и у отправителя оно есть
     */
    private boolean permitted(@NotNull CommandSender sender, @NotNull String permission) {
        return !configManager.arePermissionsEnabled() || sender.hasPermission(permission);
    }

    /**
     * Проверка права с явным отказом: при нехватке права отправляет
     * сообщение no_permission.
     *
     * @param sender     отправитель
     * @param permission право
     * @return true если действие разрешено
     */
    private boolean hasPermission(@NotNull CommandSender sender, @NotNull String permission) {

        if (permitted(sender, permission)) {
            return true;
        }

        sendMessage(sender, configManager.getNoPermissionMessage());
        return false;

    }

    private void sendMessage(@NotNull CommandSender sender, @NotNull String message) {
        messageService.sendLine(sender, message);
    }

    /**
     * Оставляет только значения, начинающиеся с введённого текста.
     * Регистр приводится через {@link Locale#ROOT}, чтобы сравнение
     * не зависело от локали сервера.
     *
     * @param values список вариантов
     * @param token  введённый текст
     * @return отфильтрованный список
     */
    public static @NotNull List<String> filter(@NotNull List<String> values, @NotNull String token) {
        String lowerToken = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerToken))
                .toList();
    }

}
