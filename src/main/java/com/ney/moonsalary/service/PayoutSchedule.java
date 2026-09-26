package com.ney.moonsalary.service;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.storage.PayoutRepository;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Расписание персональных выплат.
 * <p>
 * Дедлайны хранятся в игровых тиках: лаги сервера и тестовые среды
 * не искажают окна. Для персистентности дедлайн конвертируется
 * в unix-время при сохранении и обратно в тики при загрузке, поэтому
 * окно переживает рестарт сервера. Формат хранения выбирает пользователь
 * ({@link PayoutRepository}: YAML, SQLite, MySQL, H2).
 * <p>
 * Окно выплаты всегда потребляется целиком - "догоняющих" выплат
 * после лагов или простоя не бывает, экономика не получает дыр.
 * <p>
 * Все вызовы происходят из основного потока сервера (задачи, события,
 * команды), поэтому состояние хранится в обычной {@link HashMap}.
 */
public class PayoutSchedule {

    private static final long MILLIS_PER_TICK = 50L;

    private final MoonSalaryConfig configManager;
    private final PayoutRepository repository;
    private final LongSupplier tickClock;
    private final LongSupplier wallClock;

    /** Тик следующей выплаты игрока */
    private final Map<UUID, Long> nextPayoutAt = new HashMap<>();

    /** Тик, с которого игрок стоит в AFK (PERSONAL + pause_while_afk) */
    private final Map<UUID, Long> afkPausedSince = new HashMap<>();

    /** Тик последнего глобального payday (или старта планировщика) */
    private long globalAnchorTicks;

    public PayoutSchedule(@NotNull MoonSalaryConfig configManager,
                          @NotNull PayoutRepository repository,
                          @NotNull LongSupplier tickClock,
                          @NotNull LongSupplier wallClock) {

        this.configManager = configManager;
        this.repository = repository;
        this.tickClock = tickClock;
        this.wallClock = wallClock;
        this.globalAnchorTicks = tickClock.getAsLong();

    }

    /**
     * Текущий тик сервера - источник игрового времени расписания.
     *
     * @return тик
     */
    public long currentTicks() {
        return tickClock.getAsLong();
    }

    /**
     * Возвращает миллисекунды до следующей выплаты игрока в текущем режиме.
     * PERSONAL - до личного окна, GLOBAL - до ближайшего payday.
     *
     * @param player   игрок
     * @param nowTicks текущий тик
     * @return оставшиеся миллисекунды, не меньше нуля
     */
    public long millisUntilNext(@NotNull Player player, long nowTicks) {

        long target;

        if (configManager.getPayoutMode() == PayoutMode.PERSONAL) {
            Long next = nextPayoutAt.get(player.getUniqueId());
            target = next != null ? next : nowTicks + intervalTicks();
        } else {
            target = globalAnchorTicks + intervalTicks();
        }

        return Math.max(0L, (target - nowTicks) * MILLIS_PER_TICK);

    }

    /**
     * Ищет ближайший дедлайн выплаты среди переданных игроков.
     *
     * @param onlinePlayers онлайн-игроки для проверки
     * @return дедлайн в тиках или {@link Long#MAX_VALUE}, если дедлайнов нет
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
     * Возвращает дедлайн выплаты игрока, если он в расписании.
     *
     * @param player игрок
     * @return дедлайн в тиках или null, если игрок не отслеживается
     */
    public @Nullable Long nextDeadline(@NotNull Player player) {
        return nextPayoutAt.get(player.getUniqueId());
    }

    /**
     * Ставит игрока в расписание от текущего тика, если его там ещё нет.
     *
     * @param player игрок
     */
    public void track(@NotNull Player player) {
        track(player, currentTicks());
    }

    /**
     * Ставит игрока в расписание от заданного тика.
     * Сохранённое в хранилище окно имеет приоритет: срок не
     * сбрасывается ни рестартом, ни переподключением.
     *
     * @param player   игрок
     * @param nowTicks текущий тик
     */
    public void track(@NotNull Player player, long nowTicks) {

        UUID playerId = player.getUniqueId();

        if (nextPayoutAt.containsKey(playerId)) {
            return;
        }

        Long loadedMillis = repository.loadNextPayout(playerId);
        long value = loadedMillis != null
                ? ticksFromMillis(loadedMillis)
                : nowTicks + intervalTicks();
        nextPayoutAt.put(playerId, value);

        if (loadedMillis == null) {
            repository.saveNextPayout(playerId, millisFromTicks(value), player.getName());
        }

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
     * @param player   игрок
     * @param nowTicks текущий тик
     * @return true если выплата пора
     */
    public boolean isDue(@NotNull Player player, long nowTicks) {
        Long next = nextPayoutAt.get(player.getUniqueId());
        return next != null && nowTicks >= next;
    }

    /**
     * Продляет окно выплаты после попытки выплаты.
     * Вызывается независимо от результата, чтобы исключить повторные выплаты.
     *
     * @param player   игрок
     * @param nowTicks текущий тик
     */
    public void advance(@NotNull Player player, long nowTicks) {

        long value = nowTicks + intervalTicks();

        nextPayoutAt.put(player.getUniqueId(), value);
        repository.saveNextPayout(player.getUniqueId(), millisFromTicks(value), player.getName());

    }

    /**
     * Убирает игрока из расписания (выход с сервера).
     * Сохранённое окно при этом остаётся в хранилище.
     *
     * @param player игрок
     */
    public void remove(@NotNull Player player) {
        nextPayoutAt.remove(player.getUniqueId());
        afkPausedSince.remove(player.getUniqueId());
    }

    public void clear() {
        nextPayoutAt.clear();
        afkPausedSince.clear();
    }

    /**
     * Правда ли, что AFK-простой не должен приближать выплату
     * (PERSONAL-режим и включённый pause_while_afk).
     */
    private boolean pausesOnAfk() {
        return configManager.getPayoutMode() == PayoutMode.PERSONAL
                && configManager.isPayoutPausedWhileAfk();
    }

    /**
     * Игрок ушёл в AFK: запоминаем тик начала простоя.
     *
     * @param player   игрок
     * @param nowTicks текущий тик
     */
    public void startAfkPause(@NotNull Player player, long nowTicks) {

        if (!pausesOnAfk()) {
            return;
        }

        afkPausedSince.put(player.getUniqueId(), nowTicks);

    }

    /**
     * Игрок вернулся из AFK: сдвигаем дедлайн на длительность простоя -
     * это время не считается в окне выплаты.
     *
     * @param player   игрок
     * @param nowTicks текущий тик
     */
    public void endAfkPause(@NotNull Player player, long nowTicks) {

        Long since = afkPausedSince.remove(player.getUniqueId());

        if (since == null) {
            return;
        }

        postpone(player, nowTicks - since);

    }

    /**
     * Дедлайн наступил, а игрок всё ещё в AFK: окно скользит вперёд
     * на накопленный простой вместо выплаты или блокировки.
     *
     * @param player   игрок
     * @param nowTicks текущий тик
     * @return true если окно сдвинуто (выплату нужно пропустить)
     */
    public boolean slideOverAfk(@NotNull Player player, long nowTicks) {

        if (!pausesOnAfk()) {
            return false;
        }

        Long since = afkPausedSince.get(player.getUniqueId());

        if (since == null) {
            // Метка AFK старше паузы (например, pause_while_afk включили
            // перезагрузкой прямо во время простоя): считаем простой
            // не дольше одного периода проверки, чтобы окно не стояло на месте
            since = nowTicks - configManager.getAfkCheckIntervalTicks();
        }

        postpone(player, nowTicks - since);
        afkPausedSince.put(player.getUniqueId(), nowTicks);

        return true;

    }

    /**
     * Сдвигает дедлайн игрока вперёд на tickCount и синхронизирует хранилище.
     *
     * @param player    игрок
     * @param tickCount на сколько тиков сдвинуть
     */
    private void postpone(@NotNull Player player, long tickCount) {

        UUID playerId = player.getUniqueId();
        Long current = nextPayoutAt.get(playerId);

        if (current == null || tickCount <= 0L) {
            return;
        }

        long value = current + tickCount;
        nextPayoutAt.put(playerId, value);
        repository.saveNextPayout(playerId, millisFromTicks(value), player.getName());

    }

    /**
     * Фиксирует момент старта глобального цикла (запуск планировщика, reload).
     *
     * @param nowTicks текущий тик
     */
    public void anchorGlobal(long nowTicks) {
        this.globalAnchorTicks = nowTicks;
    }

    /**
     * Отмечает состоявшийся глобальный payday: следующее окно = сейчас + интервал.
     *
     * @param nowTicks текущий тик
     */
    public void advanceGlobal(long nowTicks) {
        this.globalAnchorTicks = nowTicks;
    }

    private long intervalTicks() {
        return configManager.getSalaryIntervalTicks();
    }

    /**
     * Переводит сохранённое unix-время в тик дедлайна: сколько миллисекунд
     * осталось до дедлайна, столько тиков и добавляем к текущему.
     * Просроченный за время рестарта дедлайн становится текущим тиком.
     *
     * @param millis сохранённый момент
     * @return тик дедлайна
     */
    private long ticksFromMillis(long millis) {
        long millisLeft = millis - wallClock.getAsLong();
        return currentTicks() + Math.max(0L, millisLeft) / MILLIS_PER_TICK;
    }

    private long millisFromTicks(long ticks) {
        return wallClock.getAsLong() + (ticks - currentTicks()) * MILLIS_PER_TICK;
    }

}
