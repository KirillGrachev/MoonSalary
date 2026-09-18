package com.ney.moonsalary.command;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.AfkState;
import com.ney.moonsalary.registry.GroupRegistry;
import com.ney.moonsalary.registry.SalaryGroup;
import com.ney.moonsalary.service.AfkTracker;
import com.ney.moonsalary.service.MessageService;
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

/**
 * Команда /salary - информация о зарплате, список групп и перезагрузка.
 */
public class SalaryCommand implements TabExecutor {

    private static final String ARG_INFO = "info";
    private static final String ARG_RELOAD = "reload";
    private static final String ARG_LIST = "list";

    private final MoonSalary plugin;
    private final ConfigManager configManager;
    private final GroupRegistry groupRegistry;
    private final AfkTracker afkTracker;
    private final MessageService messageService;
    private final TaskScheduler taskScheduler;

    public SalaryCommand(@NotNull MoonSalary plugin,
                         @NotNull ConfigManager configManager,
                         @NotNull GroupRegistry groupRegistry,
                         @NotNull AfkTracker afkTracker,
                         @NotNull MessageService messageService,
                         @NotNull TaskScheduler taskScheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.groupRegistry = groupRegistry;
        this.afkTracker = afkTracker;
        this.messageService = messageService;
        this.taskScheduler = taskScheduler;
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

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {

            case ARG_RELOAD -> reload(sender);

            case ARG_LIST -> sendList(sender);

            case ARG_INFO -> {

                if (args.length > 1 && sender.hasPermission(configManager.getPermissionList())) {

                    sendInfo(sender, findPlayer(sender, args[1]));

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

            if (sender.hasPermission(configManager.getPermissionReload())) {
                suggestions.add(ARG_RELOAD);
            }

            if (sender.hasPermission(configManager.getPermissionList())) {
                suggestions.add(ARG_LIST);
            }

            return filter(suggestions, args[0]);

        }

        if (args.length == 2 && ARG_INFO.equalsIgnoreCase(args[0])
                && sender.hasPermission(configManager.getPermissionList())) {

            return filter(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .toList(), args[1]);

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

        sendMessage(sender, header);

        for (SalaryGroup group : groups) {

            String entry = configManager.getListEntry()
                    .replace("{group}", group.getName())
                    .replace("{money}", messageService.formatMoney(group.getSalary()))
                    .replace("{priority}", String.valueOf(group.getPriority()));

            sendMessage(sender, entry);

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

        plugin.reloadConfig();

        configManager.reload();
        groupRegistry.reloadRegistry();
        afkTracker.clear();
        taskScheduler.reschedule();

        sendMessage(sender, configManager.getReloadSuccessMessage());

    }

    private @NotNull String resolveStatus(@NotNull Player target, @Nullable SalaryGroup group) {

        if (group == null) {
            return configManager.getStatusNoGroup();
        }

        if (!target.isOnline()) {
            return configManager.getStatusOffline();
        }

        AfkState afkState = afkTracker.isAfk(target) ? AfkState.AFK : AfkState.ACTIVE;

        return afkState == AfkState.AFK
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

    private boolean hasPermission(@NotNull CommandSender sender, @NotNull String permission) {

        if (!configManager.arePermissionsEnabled() || sender.hasPermission(permission)) {
            return true;
        }

        sendMessage(sender, configManager.getNoPermissionMessage());
        return false;

    }

    private void sendMessage(@NotNull CommandSender sender, @NotNull String message) {

        if (message.isEmpty()) {
            return;
        }

        sender.sendMessage(message);

    }

    /**
     * Оставляет только значения, начинающиеся с введённого текста.
     *
     * @param values список вариантов
     * @param token  введённый текст
     * @return отфильтрованный список
     */
    public static @NotNull List<String> filter(@NotNull List<String> values, @NotNull String token) {

        String lowerToken = token.toLowerCase();

        return values.stream()
                .filter(value -> value.toLowerCase().startsWith(lowerToken))
                .toList();

    }
}
