package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.PayoutMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Расписание персональных выплат.
 * <p>
 * Хранит момент следующей выплаты для каждого игрока: отсчёт стартует
 * с момента захода на сервер и продляется после каждой выплаты.
 * Окно выплаты всегда потребляется целиком - "догоняющих" выплат
 * после лагов или простоя не бывает, экономика не получает дыр.
 */
public class PayoutSchedule {

    private final ConfigManager configManager;

    /** Unix-время (мс) следующей выплаты игрока */
    private final Map<UUID, Long> nextPayoutAt = new ConcurrentHashMap<>();

    /** Момент последнего глобального payday (или старта планировщика) */
    private volatile long globalAnchor = System.currentTimeMillis();

    public PayoutSchedule(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Возвращает время до следующей выплаты игрока в текущем режиме.
     * PERSONAL - до личного окна, GLOBAL - до ближайшего payday.
     *
     * @param player игрок
     * @param now    текущее время (мс)
     * @return оставшиеся миллисекунды, не меньше нуля
     */
    public long millisUntilNext(@NotNull Player player, long now) {

        if (configManager.getPayoutMode() == PayoutMode.PERSONAL) {

            Long next = nextPayoutAt.get(player.getUniqueId());
            long target = next != null ? next : now + intervalMillis();

            return Math.max(0L, target - now);

        }

        return Math.max(0L, globalAnchor + intervalMillis() - now);

    }

    /**
     * Ищет ближайший дедлайн выплаты среди переданных игроков.
     *
     * @param onlinePlayers онлайн-игроки для проверки
     * @return дедлайн или {@link Long#MAX_VALUE}, если дедлайнов нет
     */
    public long nearestDeadline(@NotNull Collection<? extends Player> onlinePlayers) {

        long nearest = Long.MAX_VALUE;

        for (Player player : onlinePlayers) {

            Long deadline = nextPayoutAt.get(player.getUniqueId());
            if (deadline != null && deadline < nearest) {
                nearest = deadline;
            }

        }

        return nearest;

    }

    /**
     * Фиксирует момент старта глобального цикла (запуск планировщика, reload).
     *
     * @param now текущее время (мс)
     */
    public void anchorGlobal(long now) {
        this.globalAnchor = now;
    }

    /**
     * Отмечает состоявшийся глобальный payday: следующее окно = сейчас + интервал.
     *
     * @param now текущее время (мс)
     */
    public void advanceGlobal(long now) {
        this.globalAnchor = now;
    }

    /**
     * Ставит игрока в расписание, если его там ещё нет.
     *
     * @param player игрок
     */
    public void track(@NotNull Player player) {
        track(player, System.currentTimeMillis());
    }

    /**
     * Ставит игрока в расписание от заданного момента.
     *
     * @param player игрок
     * @param now    текущее время (мс)
     */
    public void track(@NotNull Player player, long now) {
        nextPayoutAt.putIfAbsent(player.getUniqueId(), now + intervalMillis());
    }

    /**
     * Проверяет, находится ли игрок в расписании.
     *
     * @param player игрок
     * @return true если момент следующей выплаты установлен
     */
    public boolean isTracked(@NotNull Player player) {
        return nextPayoutAt.containsKey(player.getUniqueId());
    }

    /**
     * Проверяет, подошло ли время выплаты игрока.
     *
     * @param player игрок
     * @param now    текущее время (мс)
     * @return true если выплата пора
     */
    public boolean isDue(@NotNull Player player, long now) {

        Long next = nextPayoutAt.get(player.getUniqueId());
        return next != null && now >= next;

    }

    /**
     * Продляет окно выплаты после попытки выплаты.
     * Вызывается независимо от результата, чтобы исключить повторные выплаты.
     *
     * @param player игрок
     * @param now    текущее время (мс)
     */
    public void advance(@NotNull Player player, long now) {
        nextPayoutAt.put(player.getUniqueId(), now + intervalMillis());
    }

    /**
     * Убирает игрока из расписания (выход с сервера).
     *
     * @param player игрок
     */
    public void remove(@NotNull Player player) {
        nextPayoutAt.remove(player.getUniqueId());
    }

    public void clear() {
        nextPayoutAt.clear();
    }

    private long intervalMillis() {
        return configManager.getSalaryIntervalMillis();
    }
}
