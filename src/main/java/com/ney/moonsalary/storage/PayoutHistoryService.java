package com.ney.moonsalary.storage;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.registry.SalaryGroup;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Журнал выплат: копит записи в памяти и сбрасывает их в хранилище
 * асинхронным таском, чтобы payday на большом онлайне не тормозил
 * основной поток сетевыми/файловыми записями.
 * <p>
 * Последняя выплата игрока отдаётся из кэша, поэтому плейсхолдер
 * {last_payout} не читает хранилище на каждую строку сообщения.
 * <p>
 * При history_limit: 0 записи не создаются вовсе.
 */
public class PayoutHistoryService {

    private static final long FLUSH_PERIOD_TICKS = 200L;

    private final MoonSalaryConfig configManager;
    private final PayoutRepository repository;
    private final Queue<PayoutEntry> buffer = new ConcurrentLinkedQueue<>();
    private final Map<UUID, PayoutEntry> lastCache = new ConcurrentHashMap<>();

    private @Nullable BukkitTask flushTask;

    public PayoutHistoryService(@NotNull MoonSalaryConfig configManager,
                                @NotNull PayoutRepository repository) {
        this.configManager = configManager;
        this.repository = repository;
    }

    /**
     * Запускает периодический асинхронный сброс журнала.
     * Повторный вызов безопасен.
     *
     * @param plugin плагин-владелец таска
     */
    public void start(@NotNull Plugin plugin) {

        if (flushTask != null) {
            return;
        }

        flushTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flush, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);

    }

    /**
     * Регистрирует попытку выплаты в журнале.
     *
     * @param player  игрок
     * @param group   группа зарплат
     * @param amount  сумма выплаты
     * @param outcome исход попытки
     * @param source  источник выплаты: расписание или ручная команда
     */
    public void record(@NotNull Player player,
                       @NotNull SalaryGroup group,
                       double amount,
                       @NotNull PayoutOutcome outcome,
                       @NotNull PayoutSource source) {

        if (configManager.getHistoryLimit() <= 0) {
            return;
        }

        PayoutEntry entry = new PayoutEntry(player.getUniqueId(), player.getName(),
                System.currentTimeMillis(), amount, group.getName(), outcome, source);

        buffer.add(entry);
        lastCache.merge(player.getUniqueId(), entry,
                (older, newer) -> newer.paidAt() >= older.paidAt() ? newer : older);

    }

    /**
     * Последняя запись истории игрока (для плейсхолдера {last_payout}).
     * Сначала кэш и буфер, затем однократное чтение из хранилища.
     *
     * @param playerId uuid игрока
     * @return запись или пусто, если истории нет
     */
    public @NotNull Optional<PayoutEntry> last(@NotNull UUID playerId) {

        PayoutEntry cached = lastCache.get(playerId);

        if (cached != null) {
            return Optional.of(cached);
        }

        PayoutEntry buffered = newestBuffered(playerId);
        List<PayoutEntry> stored = repository.loadHistory(playerId, 1);

        PayoutEntry result;

        if (stored.isEmpty()) {
            result = buffered;
        } else if (buffered != null && buffered.paidAt() >= stored.get(0).paidAt()) {
            result = buffered;
        } else {
            result = stored.get(0);
        }

        if (result != null) {
            lastCache.put(playerId, result);
        }

        return Optional.ofNullable(result);

    }

    /**
     * Синхронно сбрасывает буфер в хранилище.
     * Вызывается асинхронным таском и при остановке плагина.
     */
    public void flush() {

        List<PayoutEntry> batch = drain();

        if (batch.isEmpty()) {
            return;
        }

        repository.appendHistory(batch, configManager.getHistoryLimit());

    }

    /**
     * Останавливает таск и сбрасывает остаток буфера.
     */
    public void close() {

        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }

        flush();

    }

    private @NotNull List<PayoutEntry> drain() {

        List<PayoutEntry> batch = new ArrayList<>();

        PayoutEntry entry;
        while ((entry = buffer.poll()) != null) {
            batch.add(entry);
        }

        return batch;

    }

    private @Nullable PayoutEntry newestBuffered(@NotNull UUID playerId) {

        PayoutEntry newest = null;

        for (PayoutEntry entry : buffer) {
            if (entry.playerId().equals(playerId) && (newest == null || entry.paidAt() >= newest.paidAt())) {
                newest = entry;
            }
        }

        return newest;

    }

}
