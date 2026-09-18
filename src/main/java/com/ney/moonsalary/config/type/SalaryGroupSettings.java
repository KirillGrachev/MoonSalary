package com.ney.moonsalary.config.type;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Настройки одной группы зарплат из конфигурации.
 *
 * @param name     название группы (совпадает с permission group.<name>)
 * @param salary   сумма выплаты
 * @param priority приоритет группы (чем выше - тем важнее)
 * @param messages сообщения, отправляемые при выплате
 * @param commands команды, выполняемые при выплате
 */
public record SalaryGroupSettings(@NotNull String name,
                                  double salary,
                                  int priority,
                                  @NotNull List<String> messages,
                                  @NotNull List<String> commands) {

}
