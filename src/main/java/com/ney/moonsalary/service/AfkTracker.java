package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отслеживание AFK-состояния игроков.
 * <p>
 * Состояние хранится отдельно для каждого игрока, поэтому
 * один AFK-игрок больше не влияет на выплаты остальным.
 */
public class AfkTracker {

    private final ConfigManager configManager;

    /** Последняя зафиксированная позиция игрока (null - игрок двигался) */
    private final Map<UUID, LocationSnapshot> lastLocations = new ConcurrentHashMap<>();

    /** Игроки, которые сейчас находятся в AFK */
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();

    public AfkTracker(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Проверяет, ушёл ли игрок в AFK.
     * Игроки с правом обхода никогда не считаются AFK.
     *
     * @param player игрок
     * @return true если игрок в AFK и не имеет права обхода
     */
    public boolean isAfk(@NotNull Player player) {

        if (!configManager.isAfkEnabled()) {
            return false;
        }

        if (hasAfkBypass(player)) {
            return false;
        }

        return afkPlayers.contains(player.getUniqueId());

    }

    /**
     * Проверяет наличие права обхода AFK-защиты.
     *
     * @param player игрок
     * @return true если игрок может получать зарплату стоя на месте
     */
    public boolean hasAfkBypass(@NotNull Player player) {

        return configManager.arePermissionsEnabled()
                && player.hasPermission(configManager.getPermissionBypassAfk());

    }

    /**
     * Фиксирует текущую позицию игрока как точку отсчёта простоя.
     *
     * @param player игрок
     */
    public void startTracking(@NotNull Player player) {
        lastLocations.putIfAbsent(player.getUniqueId(), LocationSnapshot.of(player.getLocation()));
    }

    /**
     * Проверяет, изменилась ли позиция игрока с момента последней фиксации.
     *
     * @param player игрок
     * @return true если игрок переместился (или точка отсчёта ещё не создана)
     */
    public boolean hasMoved(@NotNull Player player) {

        LocationSnapshot snapshot = lastLocations.get(player.getUniqueId());
        if (snapshot == null) return true;

        return !snapshot.matches(player.getLocation(), configManager.isAfkRotationIgnored());

    }

    /**
     * Возвращает время (в миллисекундах), которое игрок стоит на месте.
     *
     * @param player игрок
     * @return 0 если игрок двигался или ещё не отслеживается
     */
    public long getIdleTime(@NotNull Player player) {

        LocationSnapshot snapshot = lastLocations.get(player.getUniqueId());
        if (snapshot == null) return 0L;

        return System.currentTimeMillis() - snapshot.recordedAt();

    }

    /**
     * Проверяет, превышен ли порог простоя.
     *
     * @param player игрок
     * @return true если игрок стоит на месте дольше configured порога
     */
    public boolean isIdleTooLong(@NotNull Player player) {
        return getIdleTime(player) >= configManager.getAfkThresholdMillis();
    }

    /**
     * Отмечает игрока как AFK.
     *
     * @param player игрок
     * @return true если состояние изменилось (игрок не был AFK до этого)
     */
    public boolean markAfk(@NotNull Player player) {
        return afkPlayers.add(player.getUniqueId());
    }

    /**
     * Снимает с игрока отметку AFK.
     *
     * @param player игрок
     * @return true если состояние изменилось (игрок был AFK до этого)
     */
    public boolean markActive(@NotNull Player player) {
        return afkPlayers.remove(player.getUniqueId());
    }

    /**
     * Проверяет, отмечен ли игрок как AFK (без учёта прав обхода).
     *
     * @param player игрок
     * @return true если игрок отмечен как AFK
     */
    public boolean isMarked(@NotNull Player player) {
        return afkPlayers.contains(player.getUniqueId());
    }

    /**
     * Удаляет все данные игрока (например, при выходе с сервера).
     *
     * @param player игрок
     */
    public void remove(@NotNull Player player) {

        lastLocations.remove(player.getUniqueId());
        afkPlayers.remove(player.getUniqueId());

    }

    public void clear() {

        lastLocations.clear();
        afkPlayers.clear();

    }

    /**
     * Снимок позиции игрока с временем фиксации.
     *
     * @param x          координата X
     * @param y          координата Y
     * @param z          координата Z
     * @param yaw        поворот по горизонтали
     * @param pitch      поворот по вертикали
     * @param worldName  название мира
     * @param recordedAt время фиксации (System.currentTimeMillis)
     */
    private record LocationSnapshot(double x, double y, double z,
                                    float yaw, float pitch,
                                    @Nullable String worldName,
                                    long recordedAt) {

        private static @NotNull LocationSnapshot of(@NotNull Location location) {

            return new LocationSnapshot(
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(),
                    location.getWorld() != null ? location.getWorld().getName() : null,
                    System.currentTimeMillis()
            );

        }

        /**
         * Сравнивает снимок с текущей позицией.
         *
         * @param location      текущая позиция игрока
         * @param ignoreRotation игнорировать ли поворот головы
         * @return true если позиция не изменилась
         */
        private boolean matches(@NotNull Location location, boolean ignoreRotation) {

            String currentWorld = location.getWorld() != null ? location.getWorld().getName() : null;

            if (worldName == null ? currentWorld != null : !worldName.equals(currentWorld)) {
                return false;
            }

            if (Double.compare(x, location.getX()) != 0
                    || Double.compare(y, location.getY()) != 0
                    || Double.compare(z, location.getZ()) != 0) {
                return false;
            }

            if (ignoreRotation) {
                return true;
            }

            return Float.compare(yaw, location.getYaw()) == 0
                    && Float.compare(pitch, location.getPitch()) == 0;

        }
    }
}
