package com.ney.moonsalary.command;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.service.ConsoleService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Регистрация команд плагина в одном месте.
 */
public class CommandDispatcher {

    private final MoonSalary plugin;
    private final ConsoleService consoleService;

    public CommandDispatcher(@NotNull MoonSalary plugin, @NotNull ConsoleService consoleService) {
        this.plugin = plugin;
        this.consoleService = consoleService;
    }

    /**
     * Регистрирует обработчики команд.
     *
     * @param command  название команды из plugin.yml
     * @param executor обработчик команды и автодополнения
     */
    public void registerCommand(@NotNull String command, @NotNull TabExecutor executor) {

        PluginCommand pluginCommand = plugin.getCommand(command);

        if (pluginCommand == null) {
            consoleService.log(ConsoleMessage.COMMAND_MISSING, "command", command);
            return;
        }

        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(executor);

    }

    /**
     * Удаляет команду и её алиасы из command map сервера.
     * <p>
     * Используется при отключении плагина: команда не должна оставаться
     * «зомби» и бросать CommandException у выключенного плагина.
     *
     * @param command название команды из plugin.yml
     */
    public void unregisterCommand(@NotNull String command) {

        PluginCommand pluginCommand = plugin.getCommand(command);

        if (pluginCommand == null) {
            return;
        }

        try {

            Method getCommandMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
            CommandMap commandMap = (CommandMap) getCommandMap.invoke(Bukkit.getServer());
            pluginCommand.unregister(commandMap);
            removeFromKnownCommands(commandMap, pluginCommand);
            syncCommands();

        } catch (ReflectiveOperationException | RuntimeException exception) {
            consoleService.log(ConsoleMessage.COMMAND_UNREGISTER_FAILED,
                    "command", command,
                    "reason", String.valueOf(exception.getMessage()));
        }

    }

    /**
     * Просит сервер пересобрать дерево команд (Paper/Brigadier),
     * чтобы вырегистрированная команда исчезла и из автодополнения.
     */
    private void syncCommands() {
        try {

            Method syncCommands = Bukkit.getServer().getClass().getMethod("syncCommands");
            syncCommands.invoke(Bukkit.getServer());

        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // сервер без syncCommands: команда исчезнет после рестарта
        }
    }

    /**
     * Удаляет команду и алиасы из knownCommands: Command#unregister сам этого
     * не делает, без этой чистки dispatch продолжал бы находить «зомби».
     *
     * @param commandMap    command map сервера
     * @param pluginCommand команда
     */
    private void removeFromKnownCommands(@NotNull CommandMap commandMap,
                                         @NotNull PluginCommand pluginCommand) {

        Map<String, Command> knownCommands = knownCommands(commandMap);

        if (knownCommands == null) {
            return;
        }

        knownCommands.remove(pluginCommand.getName().toLowerCase(Locale.ROOT));
        knownCommands.remove(pluginCommand.getLabel().toLowerCase(Locale.ROOT));

        for (String alias : pluginCommand.getAliases()) {
            knownCommands.remove(alias.toLowerCase(Locale.ROOT));
        }

        String prefixed = plugin.getName().toLowerCase(Locale.ROOT) + ":";
        knownCommands.remove(prefixed + pluginCommand.getName().toLowerCase(Locale.ROOT));

        for (String alias : pluginCommand.getAliases()) {
            knownCommands.remove(prefixed + alias.toLowerCase(Locale.ROOT));
        }

    }

    private @Nullable Map<String, Command> knownCommands(@NotNull CommandMap commandMap) {

        // Paper отдаёт карту публичным геттером
        try {

            Method getKnownCommands = commandMap.getClass().getMethod("getKnownCommands");
            return (Map<String, Command>) getKnownCommands.invoke(commandMap);

        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // spigot без геттера: читаем защищённое поле ниже
        }

        // В чистом Spigot knownCommands - protected поле SimpleCommandMap
        try {

            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);

        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }

    }

}
