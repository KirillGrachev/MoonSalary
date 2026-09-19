package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Обёртка над Vault Economy.
 */
public class EconomyService {

    private final MoonSalary plugin;
    private final ConsoleService consoleService;
    private @Nullable Economy economy;

    public EconomyService(@NotNull MoonSalary plugin, @NotNull ConsoleService consoleService) {
        this.plugin = plugin;
        this.consoleService = consoleService;
    }

    /**
     * Подключается к экономике через Vault.
     *
     * @return true если экономика успешно подключена
     */
    public boolean setup() {

        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        var registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (registration == null) {
            return false;
        }

        this.economy = registration.getProvider();

        consoleService.log(ConsoleMessage.ECONOMY_HOOKED, "provider", economy.getName());
        return true;

    }

    /**
     * Проверяет доступность экономики.
     *
     * @return true если экономика подключена
     */
    public boolean isAvailable() {
        return economy != null;
    }

    /**
     * Выдаёт игроку деньги.
     *
     * @param player игрок
     * @param amount сумма
     * @return true если операция прошла успешно
     */
    public boolean deposit(@NotNull Player player, double amount) {

        if (economy == null || amount <= 0D) {
            return false;
        }

        try {

            EconomyResponse response = economy.depositPlayer(player, amount);

            if (!response.transactionSuccess()) {

                consoleService.log(ConsoleMessage.DEPOSIT_FAILED,
                        "money", String.valueOf(amount),
                        "player", player.getName(),
                        "reason", String.valueOf(response.errorMessage));
                return false;

            }

            return true;

        } catch (RuntimeException exception) {

            consoleService.log(ConsoleMessage.DEPOSIT_EXCEPTION,
                    "player", player.getName(),
                    "reason", String.valueOf(exception.getMessage()));
            return false;

        }
    }

    /**
     * Форматирует сумму через экономику (символ валюты и т.д.).
     *
     * @param amount сумма
     * @return отформатированная строка
     */
    public @NotNull String format(double amount) {

        if (economy == null) {
            return String.valueOf(amount);
        }

        try {
            return economy.format(amount);
        } catch (RuntimeException exception) {
            return String.valueOf(amount);
        }
    }

    public void shutdown() {
        this.economy = null;
    }
}
