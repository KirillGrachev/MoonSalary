package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
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
    private @Nullable Economy economy;

    public EconomyService(@NotNull MoonSalary plugin) {
        this.plugin = plugin;
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

        plugin.getLogger().info("Экономика подключена: " + economy.getName());
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

                plugin.getLogger().warning("Не удалось выдать " + amount + " игроку "
                        + player.getName() + ": " + response.errorMessage);
                return false;

            }

            return true;

        } catch (RuntimeException exception) {

            plugin.getLogger().warning("Ошибка экономики при выдаче зарплаты игроку "
                    + player.getName() + ": " + exception.getMessage());
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
