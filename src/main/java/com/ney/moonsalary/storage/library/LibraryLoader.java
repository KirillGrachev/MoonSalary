package com.ney.moonsalary.storage.library;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Динамическая подгрузка драйверов баз данных.
 * <p>
 * Jar-файлы лежат в папке libs плагина и скачиваются с Maven Central
 * только при первом выборе SQL-хранилища; классы драйверов поднимаются
 * отдельным classloader'ом и не затрагивают YAML-серверы. В сам jar
 * плагина драйверы не упаковываются.
 */
public class LibraryLoader {

    public static final LibraryDefinition MYSQL_DRIVER = new LibraryDefinition(
            "mysql-connector-j-8.3.0.jar",
            "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar",
            "94e7fa815370cdcefed915db7f53f88445fac110f8c3818392b992ec9ee6d295"
    );

    public static final LibraryDefinition MYSQL_PROTOBUF = new LibraryDefinition(
            "protobuf-java-3.25.1.jar",
            "https://repo1.maven.org/maven2/com/google/protobuf/protobuf-java/3.25.1/protobuf-java-3.25.1.jar",
            "48a8e58a1a8f82eff141a7a388d38dfe77d7a48d5e57c9066ee37f19147e20df"
    );

    public static final LibraryDefinition H2_DRIVER = new LibraryDefinition(
            "h2-2.2.224.jar",
            "https://repo1.maven.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar",
            "b9d8f19358ada82a4f6eb5b174c6cfe320a375b5a9cb5a4fe456d623e6e55497"
    );

    public static final LibraryDefinition SQLITE_DRIVER = new LibraryDefinition(
            "sqlite-jdbc-3.46.0.0.jar",
            "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.0.0/sqlite-jdbc-3.46.0.0.jar",
            "e697df15be3f95219d80773c5f1002030e33e932adda186c1c86fd51df6691a9"
    );

    private static final int CONNECT_TIMEOUT = 10_000;
    private static final int READ_TIMEOUT = 60_000;

    private final File libsFolder;
    private final boolean downloadEnabled;
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    public LibraryLoader(@NotNull File libsFolder, boolean downloadEnabled) {
        this.libsFolder = libsFolder;
        this.downloadEnabled = downloadEnabled;
    }

    /**
     * Гарантирует наличие библиотек и возвращает экземпляр JDBC-драйвера.
     *
     * @param libraries   набор jar для набора классов
     * @param driverClass название класса драйвера
     * @return готовый драйвер (регистрация в DriverManager не нужна)
     * @throws SQLException если файл недоступен или класс не поднялся
     */
    public @NotNull Driver loadDriver(@NotNull List<LibraryDefinition> libraries,
                                      @NotNull String driverClass) throws SQLException {

        URLClassLoader classLoader = classLoader(libraries);
        try {

            Class<?> driverType = Class.forName(driverClass, true, classLoader);
            return (Driver) driverType.getDeclaredConstructor().newInstance();

        } catch (ReflectiveOperationException exception) {
            throw new SQLException("Failed to load driver " + driverClass, exception);
        }

    }

    private @NotNull URLClassLoader classLoader(@NotNull List<LibraryDefinition> libraries) throws SQLException {

        String key = libraries.stream()
                .map(LibraryDefinition::fileName)
                .reduce("", (first, second) -> first + "," + second);

        URLClassLoader cached = classLoaders.get(key);

        if (cached != null) return cached;

        URL[] urls = new URL[libraries.size()];

        try {

            for (int i = 0; i < libraries.size(); i++) {
                urls[i] = ensure(libraries.get(i)).toURI().toURL();
            }

        } catch (MalformedURLException exception) {
            throw new SQLException("Invalid library path", exception);
        }

        URLClassLoader classLoader = new URLClassLoader(urls, getClass().getClassLoader());
        classLoaders.put(key, classLoader);

        return classLoader;

    }

    /**
     * Проверяет jar в libs: контрольная сумма совпадает - файл готов,
     * не совпадает или файла нет - скачивание заново.
     *
     * @param definition описание библиотеки
     * @return файл jar
     * @throws SQLException если библиотеки нет и скачивание выключено
     */
    private @NotNull File ensure(@NotNull LibraryDefinition definition) throws SQLException {

        File target = new File(libsFolder, definition.fileName());

        if (target.isFile() && definition.sha256().equals(sha256(target))) {
            return target;
        }

        if (!downloadEnabled) {

            throw new SQLException("Library " + definition.fileName()
                    + " is missing in " + libsFolder + " and downloading is disabled");

        }

        download(definition, target);

        if (!definition.sha256().equals(sha256(target))) {
            throw new SQLException("Checksum mismatch for " + definition.fileName());
        }

        return target;

    }

    private void download(@NotNull LibraryDefinition definition, @NotNull File target) throws SQLException {

        if (!libsFolder.isDirectory() && !libsFolder.mkdirs()) {
            throw new SQLException("Failed to create libs directory: " + libsFolder);
        }

        File part = new File(libsFolder, definition.fileName() + ".part");

        try {

            URLConnection connection = new URL(definition.url()).openConnection();

            if (connection instanceof HttpURLConnection http) {
                http.setConnectTimeout(CONNECT_TIMEOUT);
                http.setReadTimeout(READ_TIMEOUT);
            }

            try (InputStream stream = connection.getInputStream()) {
                Files.copy(stream, part.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } finally {

                if (connection instanceof HttpURLConnection http) {
                    http.disconnect();
                }

            }

            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        } catch (java.io.IOException exception) {

            throw new SQLException("Failed to download " + definition.fileName()
                    + " from " + definition.url() + ": " + exception.getMessage(), exception);

        }

    }

    /**
     * Считает SHA-256 файла.
     *
     * @param file файл
     * @return hex-строка суммы
     */
    public static @NotNull String sha256(@NotNull File file) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream stream = Files.newInputStream(file.toPath())) {

                byte[] buffer = new byte[8192];
                int read;

                while ((read = stream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }

            }

            StringBuilder hex = new StringBuilder();

            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException | java.io.IOException exception) {
            return "";
        }

    }

}
