package com.ney.moonsalary.command;

import com.ney.moonsalary.MoonSalary;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Регистрация команд плагина в одном месте.
 */
public class CommandDispatcher {

    private final MoonSalary plugin;

    public CommandDispatcher(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
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

            plugin.getLogger().warning("Команда '" + command + "' не найдена в plugin.yml.");
            return;

        }

        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(executor);

    }
}
