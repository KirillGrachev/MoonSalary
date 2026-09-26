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
 * <p>
 * {@link #setup()} идемпотентен: повторный вызов с тем же провайдером
 * не логирует и не меняет ничего. Если провайдер перерегистрировался
 * (перезагрузка EssentialsX/CMI), ссылка заменяется на свежую -
 * протухший экземпляр Economy не удерживается.
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
     * Безопасно вызывать повторно: тот же провайдер - тихий no-op,
     * новый провайдер - замена ссылки и лог.
     *
     * @return true если экономика доступна после вызова
     */
    public boolean setup() {

        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        var registration = plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (registration == null) {
            return false;
        }

        Economy provider = registration.getProvider();

        if (provider == null) {
            return false;
        }

        if (provider != this.economy) {
            this.economy = provider;
            consoleService.log(ConsoleMessage.ECONOMY_HOOKED, "provider", safeName(provider));
        }

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
                    "reason", String.valueOf(exception));
            return false;
        }

    }

    /**
     * Форматирует сумму через экономику (символ валюты и т.д.).
     * Провайдер может вернуть null или кинуть исключение - в обоих случаях
     * используется plain-число, форматирование не может ронять выплату.
     *
     * @param amount сумма
     * @return отформатированная строка
     */
    public @NotNull String format(double amount) {

        if (economy == null) {
            return String.valueOf(amount);
        }

        try {
            String formatted = economy.format(amount);
            return formatted != null ? formatted : String.valueOf(amount);
        } catch (RuntimeException exception) {
            return String.valueOf(amount);
        }

    }

    public void shutdown() {
        this.economy = null;
    }

    /**
     * Имя провайдера для лога: getName() сторонней реализации может кинуть
     * исключение, логирование не должно ронять подключение экономики.
     */
    private @NotNull String safeName(@NotNull Economy provider) {
        try {
            return provider.getName();
        } catch (RuntimeException exception) {
            return provider.getClass().getSimpleName();
        }
    }

}
