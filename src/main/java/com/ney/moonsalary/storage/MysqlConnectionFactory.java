package com.ney.moonsalary.storage;

import com.ney.moonsalary.config.type.SqlSettings;
import com.ney.moonsalary.storage.library.LibraryLoader;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.StringJoiner;

/**
 * Подключения к MySQL: драйвер поднимается лениво из папки libs
 * только при выбранном SQL-хранилище.
 */
public class MysqlConnectionFactory implements ConnectionFactory {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    private final SqlSettings settings;
    private final LibraryLoader libraryLoader;

    public MysqlConnectionFactory(@NotNull SqlSettings settings, @NotNull LibraryLoader libraryLoader) {
        this.settings = settings;
        this.libraryLoader = libraryLoader;
    }

    @Override
    public Connection open() throws SQLException {

        Driver driver = libraryLoader.loadDriver(
                List.of(LibraryLoader.MYSQL_DRIVER, LibraryLoader.MYSQL_PROTOBUF),
                DRIVER_CLASS
        );

        return driver.connect(buildUrl(), credentials());

    }

    /**
     * Собирает JDBC-URL из настроек и дополнительных параметров.
     *
     * @return URL вида jdbc:mysql://host:port/database?params
     */
    private @NotNull String buildUrl() {

        StringJoiner params = new StringJoiner("&");
        settings.properties().forEach((key, value) -> params.add(key + "=" + value));
        String query = params.length() == 0 ? "" : "?" + params;

        return "jdbc:mysql://" + settings.host() + ":" + settings.port() + "/" + settings.database() + query;

    }

    private @NotNull Properties credentials() {

        Properties properties = new Properties();

        properties.setProperty("user", settings.user());
        properties.setProperty("password", settings.password());

        return properties;

    }

}
