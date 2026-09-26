package com.ney.moonsalary.service;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.AfkState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Отслеживание AFK-состояния игроков.
 * <p>
 * Состояние хранится отдельно для каждого игрока, поэтому
 * один AFK-игрок больше не влияет на выплаты остальным.
 * <p>
 * Все вызовы происходят из основного потока сервера (задача проверки,
 * события входа/выхода, команды), поэтому состояние хранится
 * в обычных коллекциях без синхронизации.
 */
public class AfkTracker {

    private final MoonSalaryConfig configManager;

    /** Последняя зафиксированная позиция игрока */
    private final Map<UUID, LocationSnapshot> lastLocations = new HashMap<>();

    /** Накопленный простой игрока в тиках */
    private final Map<UUID, Long> idleTicks = new HashMap<>();

    /** Игроки, которые сейчас находятся в AFK */
    private final Set<UUID> afkPlayers = new HashSet<>();

    public AfkTracker(@NotNull MoonSalaryConfig configManager) {
        this.configManager = configManager;
    }

    /**
     * Обрабатывает одну проверку AFK.
     * <p>
     * Простой копится в игровых тиках, а не в wall-clock: лаги сервера
     * и тестовые среды не искажают порог.
     *
     * @param player        игрок
     * @param intervalTicks тиков, прошедших с прошлой проверки
     * @return TRUE - игрок только что ушёл в AFK, FALSE - только что вернулся,
     *         null - состояние не изменилось
     */
    public @Nullable Boolean processCheck(@NotNull Player player, long intervalTicks) {

        if (hasMoved(player)) {

            boolean wasAfk = markActive(player);
            refreshTracking(player);
            idleTicks.put(player.getUniqueId(), 0L);

            return wasAfk ? Boolean.FALSE : null;

        }

        // Снимок здесь гарантированно есть: без снимка hasMoved() вернул бы true
        long idle = idleTicks.getOrDefault(player.getUniqueId(), 0L) + intervalTicks;
        idleTicks.put(player.getUniqueId(), idle);

        if (idle >= thresholdTicks() && markAfk(player)) {
            return Boolean.TRUE;
        }

        return null;

    }

    private long thresholdTicks() {
        return configManager.getAfkThresholdMillis() / 50L;
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
     * Определяет AFK-состояние игрока с учётом настройки защиты и права обхода.
     *
     * @param player игрок
     * @return состояние AFK
     */
    public @NotNull AfkState resolveState(@NotNull Player player) {

        if (!configManager.isAfkEnabled()) {
            return AfkState.ACTIVE;
        }

        if (!isMarked(player)) {
            return AfkState.ACTIVE;
        }

        return hasAfkBypass(player) ? AfkState.BYPASSED : AfkState.AFK;

    }

    /**
     * Ставит точку отсчёта простоя, если её ещё нет (вход, самовосстановление).
     *
     * @param player игрок
     */
    public void startTracking(@NotNull Player player) {
        lastLocations.putIfAbsent(player.getUniqueId(), snapshotOf(player));
    }

    /**
     * Перезаписывает точку отсчёта текущей позицией: вызывается, когда игрок
     * действительно двинулся, - иначе старый снимок вечно считался бы движением.
     *
     * @param player игрок
     */
    public void refreshTracking(@NotNull Player player) {
        lastLocations.put(player.getUniqueId(), snapshotOf(player));
    }

    private @NotNull LocationSnapshot snapshotOf(@NotNull Player player) {
        return LocationSnapshot.of(player.getLocation());
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
        idleTicks.remove(player.getUniqueId());
        afkPlayers.remove(player.getUniqueId());

    }

    public void clear() {

        lastLocations.clear();
        idleTicks.clear();
        afkPlayers.clear();

    }

    /**
     * Снимок позиции игрока.
     *
     * @param x         координата X
     * @param y         координата Y
     * @param z         координата Z
     * @param yaw       поворот по горизонтали
     * @param pitch     поворот по вертикали
     * @param worldName название мира
     */
    private record LocationSnapshot(double x, double y, double z,
                                    float yaw, float pitch,
                                    @Nullable String worldName) {

        private static @NotNull LocationSnapshot of(@NotNull Location location) {
            return new LocationSnapshot(
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(),
                    location.getWorld() != null ? location.getWorld().getName() : null
            );
        }

        /**
         * Сравнивает снимок с текущей позицией.
         *
         * @param location       текущая позиция игрока
         * @param ignoreRotation игнорировать ли поворот головы
         * @return true если позиция не изменилась
         */
        private boolean matches(@NotNull Location location, boolean ignoreRotation) {

            String currentWorld = location.getWorld() != null ? location.getWorld().getName() : null;
            if (!Objects.equals(worldName, currentWorld)) {
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
