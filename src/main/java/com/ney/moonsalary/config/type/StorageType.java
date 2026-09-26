package com.ney.moonsalary.config.type;

import java.util.Locale;

/**
 * Формат хранения окон выплат и истории зарплат.
 */
public enum StorageType {

    /** Файл payouts.yml в папке плагина. */
    YAML,

    /** Файловая база SQLite в папке плагина. */
    SQLITE,

    /** Таблицы во внешней базе MySQL. */
    MYSQL,

    /** Файловая база в папке плагина (H2, синтаксис MySQL). */
    H2;

    /**
     * Безопасно получает тип по его названию из конфига.
     *
     * @param value    название типа
     * @param fallback значение по умолчанию
     * @return найденный тип или fallback
     */
    public static StorageType of(String value, StorageType fallback) {

        if (value == null || value.isBlank()) return fallback;

        String normalized = value.trim().toUpperCase(Locale.ROOT);

        for (StorageType type : values()) {
            if (type.name().equals(normalized)) return type;
        }

        return fallback;

    }

}
