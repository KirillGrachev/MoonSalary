package com.ney.moonsalary.storage;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Одна запись истории выплат игрока.
 *
 * @param playerId uuid игрока
 * @param player   ник игрока на момент выплаты
 * @param paidAt   unix-время попытки выплаты в миллисекундах
 * @param amount   сумма выплаты
 * @param group    название группы зарплат
 * @param outcome  исход попытки
 * @param source   источник выплаты: расписание или ручная команда
 */
public record PayoutEntry(@NotNull UUID playerId,
                          @NotNull String player,
                          long paidAt,
                          double amount,
                          @NotNull String group,
                          @NotNull PayoutOutcome outcome,
                          @NotNull PayoutSource source) {
}
