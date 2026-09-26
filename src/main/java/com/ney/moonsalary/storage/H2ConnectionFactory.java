package com.ney.moonsalary.storage;

import com.ney.moonsalary.storage.library.LibraryLoader;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Файловая база в папке плагина (H2 в режиме совместимости с MySQL).
 * Драйвер поднимается лениво из папки libs только при типе хранения H2.
 */
public class H2ConnectionFactory implements ConnectionFactory {

    private static final String DRIVER_CLASS = "org.h2.Driver";
    // MODE=MySQL - единый SQL-диалект с MySQL-репозиторием;
    // DATABASE_TO_LOWER - имена таблиц/колонок в нижнем регистре, как в MySQL
    private static final String OPTIONS = ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";

    private final File databaseFile;
    private final LibraryLoader libraryLoader;

    public H2ConnectionFactory(@NotNull File databaseFile, @NotNull LibraryLoader libraryLoader) {
        this.databaseFile = databaseFile;
        this.libraryLoader = libraryLoader;
    }

    @Override
    public Connection open() throws SQLException {

        File parent = databaseFile.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Failed to create storage directory: " + parent);
        }

        Driver driver = libraryLoader.loadDriver(List.of(LibraryLoader.H2_DRIVER), DRIVER_CLASS);

        Properties properties = new Properties();

        properties.setProperty("user", "sa");
        properties.setProperty("password", "");

        return driver.connect("jdbc:h2:" + databaseFile.getAbsolutePath() + OPTIONS, properties);

    }

}
