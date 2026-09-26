package com.ney.moonsalary.storage.library;

/**
 * Описание скачиваемой библиотеки драйвера.
 *
 * @param fileName имя jar в папке libs
 * @param url      адрес jar в Maven Central
 * @param sha256   контрольная сумма содержимого
 */
public record LibraryDefinition(String fileName, String url, String sha256) {
}
