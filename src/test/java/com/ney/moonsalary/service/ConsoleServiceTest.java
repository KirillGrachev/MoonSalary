package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.type.ConsoleMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsoleServiceTest {

    private Logger logger;
    private ConsoleService consoleService;

    @BeforeEach
    void setUp() {

        logger = mock(Logger.class);
        MoonSalary plugin = mock(MoonSalary.class);

        when(plugin.getLogger()).thenReturn(logger);
        this.consoleService = new ConsoleService(plugin);

    }

    @Test
    @DisplayName("Текст берётся из кода (enum), плейсхолдеры подставляются парами")
    void logsEnumTextWithTokens() {
        consoleService.log(ConsoleMessage.STARTUP, "groups", "8");
        verify(logger).log(Level.INFO, "MoonSalary is up and running! Groups: 8");
    }

    @Test
    @DisplayName("Уровень логирования берётся из типа сообщения")
    void logsLevelFromMessageType() {
        consoleService.log(ConsoleMessage.ECONOMY_MISSING);
        verify(logger).log(Level.SEVERE, ConsoleMessage.ECONOMY_MISSING.getText());
    }

    @Test
    @DisplayName("&-коды в текстах преобразуются как в чате")
    void translatesColorCodes() {
        consoleService.log(ConsoleMessage.GROUPS_EMPTY);
        verify(logger).log(Level.WARNING, ConsoleMessage.GROUPS_EMPTY.getText());
    }

    @Test
    @DisplayName("applyTokens подставляет пары и игнорирует непарный хвост")
    void appliesTokenPairs() {
        assertEquals("a=1, b=2", ConsoleService.applyTokens("a={a}, b={b}", "a", "1", "b", "2"));
        assertEquals("a={a}", ConsoleService.applyTokens("a={a}", "solo"));
    }

}
