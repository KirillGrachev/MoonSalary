package com.ney.moonsalary.storage;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Источник JDBC-подключений: отделяет репозиторий от драйвера и URL.
 */
public interface ConnectionFactory {

    /**
     * Открывает новое подключение к базе данных.
     *
     * @return живое подключение
     * @throws SQLException если база недоступна
     */
    Connection open() throws SQLException;

}
