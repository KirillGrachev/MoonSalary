package com.ney.moonsalary.storage;

import com.ney.moonsalary.config.type.StorageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Контракт хранилища окон выплат и истории зарплат.
 * Реализации: payouts.yml, SQLite, MySQL, H2.
 * <p>
 * Реализации потокобезопасны: записи истории приходят из асинхронного
 * таска_flush, чтения окон - из основного потока сервера.
 */
public interface PayoutRepository {

    /**
     * Возвращает формат этого хранилища.
     *
     * @return тип хранения
     */
    @NotNull StorageType type();

    /**
     * Читает сохранённый дедлайн следующей выплаты игрока.
     *
     * @param playerId uuid игрока
     * @return момент в миллисекундах или null если не сохранён
     */
    @Nullable Long loadNextPayout(@NotNull UUID playerId);

    /**
     * Сохраняет дедлайн следующей выплаты игрока.
     *
     * @param playerId     uuid игрока
     * @param nextPayoutAt момент в миллисекундах
     * @param playerName   ник игрока (для читабельности хранилища)
     */
    void saveNextPayout(@NotNull UUID playerId, long nextPayoutAt, @NotNull String playerName);

    /**
     * Читает последние записи истории игрока (новые первыми).
     *
     * @param playerId uuid игрока
     * @param limit    сколько записей вернуть
     * @return записи истории, пустой список если истории нет
     */
    @NotNull List<PayoutEntry> loadHistory(@NotNull UUID playerId, int limit);

    /**
     * Дописывает записи истории и обрезает хвост сверх лимита на игрока.
     *
     * @param entries новые записи
     * @param limit   лимит записей на игрока
     */
    void appendHistory(@NotNull List<PayoutEntry> entries, int limit);

    /**
     * Освобождает ресурсы хранилища.
     */
    void close();

}
