package com.ney.moonsalary.config.type;

import java.util.Map;

/**
 * Подключение к MySQL для хранения окон выплат и истории.
 *
 * @param host       адрес сервера базы данных
 * @param port       порт сервера базы данных
 * @param database   название базы данных
 * @param table      базовое имя таблиц (суффиксы _players и _history добавляются сами)
 * @param user       пользователь базы данных
 * @param password   пароль пользователя
 * @param properties дополнительные параметры JDBC-URL
 */
public record SqlSettings(String host,
                          int port,
                          String database,
                          String table,
                          String user,
                          String password,
                          Map<String, String> properties) {
}
