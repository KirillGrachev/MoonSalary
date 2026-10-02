package com.ney.moonsalary.storage.library;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryLoaderTest {

    @TempDir
    private Path libsFolder;

    @Test
    @DisplayName("Без файла и без скачивания драйвер не поднимается")
    void missingLibraryWithoutDownloadFails() {
        LibraryLoader loader = new LibraryLoader(libsFolder.toFile(), false);
        assertThrows(SQLException.class, () -> loader.loadDriver(
                List.of(LibraryLoader.H2_DRIVER), "org.h2.Driver"));
    }

    @Test
    @DisplayName("Битая контрольная сумма требует перекачивания")
    void checksumMismatchForcesRedownload() throws Exception {

        File broken = libsFolder.resolve(LibraryLoader.H2_DRIVER.fileName()).toFile();
        Files.writeString(broken.toPath(), "not a jar");

        LibraryLoader loader = new LibraryLoader(libsFolder.toFile(), false);

        assertThrows(SQLException.class, () -> loader.loadDriver(
                List.of(LibraryLoader.H2_DRIVER), "org.h2.Driver"));

    }

    @Test
    @DisplayName("Драйвер скачивается с Maven Central и поднимается отдельным classloader")
    void downloadsAndLoadsRealDriver() throws Exception {

        LibraryLoader loader = new LibraryLoader(libsFolder.toFile(), true);

        Driver driver = loader.loadDriver(List.of(LibraryLoader.H2_DRIVER), "org.h2.Driver");

        assertNotNull(driver);

        assertTrue(new File(libsFolder.toFile(), LibraryLoader.H2_DRIVER.fileName()).isFile());
        assertEquals(LibraryLoader.H2_DRIVER.sha256(),
                LibraryLoader.sha256(new File(libsFolder.toFile(), LibraryLoader.H2_DRIVER.fileName())));

    }

    @Test
    @DisplayName("sha256 считает сумму содержимого")
    void sha256OfKnownContent() throws Exception {

        File file = libsFolder.resolve("sample.bin").toFile();
        Files.writeString(file.toPath(), "moon");

        // sha256("moon")
        assertEquals("9e78b43ea00edcac8299e0cc8df7f6f913078171335f733a21d5d911b6999132",
                LibraryLoader.sha256(file));

    }

}
