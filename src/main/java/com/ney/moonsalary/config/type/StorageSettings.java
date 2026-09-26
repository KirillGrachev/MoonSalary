package com.ney.moonsalary.config.type;

/**
 * Выбранный формат хранения и его настройки.
 *
 * @param type        формат хранения
 * @param sql         настройки MySQL (используются только при type = MYSQL)
 * @param historyLimit сколько записей истории выплат держать на игрока (0 - история выключена)
 */
public record StorageSettings(StorageType type, SqlSettings sql, int historyLimit) {
}
