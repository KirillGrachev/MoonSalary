package com.ney.moonsalary.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SalaryCommandTest {

    @Test
    @DisplayName("Автодополнение не зависит от регистра")
    void filtersIgnoringCase() {

        assertEquals(List.of("reload"), SalaryCommand.filter(List.of("info", "reload", "list"), "RE"));

    }

    @Test
    @DisplayName("Пустой токен возвращает все варианты")
    void returnsAllForEmptyToken() {

        assertEquals(List.of("info", "reload", "list"),
                SalaryCommand.filter(List.of("info", "reload", "list"), ""));

    }

    @Test
    @DisplayName("Несовпадающий токен возвращает пустой список")
    void returnsEmptyForUnknownToken() {

        assertTrue(SalaryCommand.filter(List.of("info", "reload"), "xyz").isEmpty());

    }
}
