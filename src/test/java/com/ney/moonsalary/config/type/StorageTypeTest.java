package com.ney.moonsalary.config.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTypeTest {

    @Test
    @DisplayName("Тип читается без учёта регистра")
    void parsesIgnoringCase() {
        assertEquals(StorageType.MYSQL, StorageType.of("mysql", StorageType.YAML));
        assertEquals(StorageType.SQLITE, StorageType.of("SQLite", StorageType.YAML));
        assertEquals(StorageType.H2, StorageType.of("h2", StorageType.YAML));
        assertEquals(StorageType.YAML, StorageType.of("yaml", StorageType.H2));
    }

    @Test
    @DisplayName("Пустое и неизвестное значение откатываются к fallback")
    void fallsBackOnUnknown() {
        assertEquals(StorageType.YAML, StorageType.of(null, StorageType.YAML));
        assertEquals(StorageType.YAML, StorageType.of("  ", StorageType.YAML));
        assertEquals(StorageType.YAML, StorageType.of("chaos", StorageType.YAML));
    }

}
