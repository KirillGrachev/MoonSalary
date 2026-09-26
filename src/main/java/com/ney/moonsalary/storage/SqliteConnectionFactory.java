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
 * Файловая база SQLite в папке плагина.
 * Драйвер поднимается лениво из папки libs только при типе хранения SQLITE.
 */
public class SqliteConnectionFactory implements ConnectionFactory {

    private static final String DRIVER_CLASS = "org.sqlite.JDBC";

    private final File databaseFile;
    private final LibraryLoader libraryLoader;

    public SqliteConnectionFactory(@NotNull File databaseFile, @NotNull LibraryLoader libraryLoader) {
        this.databaseFile = databaseFile;
        this.libraryLoader = libraryLoader;
    }

    @Override
    public Connection open() throws SQLException {

        File parent = databaseFile.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Failed to create storage directory: " + parent);
        }

        Driver driver = libraryLoader.loadDriver(List.of(LibraryLoader.SQLITE_DRIVER), DRIVER_CLASS);
        return driver.connect("jdbc:sqlite:" + databaseFile.getAbsolutePath(), new Properties());

    }

}
