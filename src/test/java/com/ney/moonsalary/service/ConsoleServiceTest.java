package com.ney.moonsalary.service;

import com.ney.moonsalary.MoonSalary;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.ConsoleMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConsoleServiceTest {

    private MoonSalary plugin;
    private Logger logger;
    private ConsoleService consoleService;

    @BeforeEach
    void setUp() {

        logger = mock(Logger.class);

        plugin = mock(MoonSalary.class);
        when(plugin.getLogger()).thenReturn(logger);

        this.consoleService = new ConsoleService(plugin);

    }

    @Test
    @DisplayName("Без конфигурации используется текст по умолчанию")
    void fallsBackToDefaultText() {

        consoleService.log(ConsoleMessage.STARTUP, "groups", "8");

        verify(logger).log(Level.INFO, "MoonSalary is up and running! Groups: 8");

    }

    @Test
    @DisplayName("Плейсхолдеры подставляются парами")
    void appliesTokenPairs() {

        assertEquals("a=1, b=2", ConsoleService.applyTokens("a={a}, b={b}", "a", "1", "b", "2"));

    }

    @Test
    @DisplayName("Шаблоны берутся из конфигурации")
    void usesConfigTemplates() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.areConsoleMessagesEnabled()).thenReturn(true);
        when(configManager.getConsoleMessage("startup", ConsoleMessage.STARTUP.getFallback()))
                .thenReturn("Custom start: {groups} groups");

        consoleService.attach(configManager);
        consoleService.log(ConsoleMessage.STARTUP, "groups", "3");

        verify(logger).log(Level.INFO, "Custom start: 3 groups");

    }

    @Test
    @DisplayName("messages.console.enabled: false полностью глушит консоль")
    void silencesConsoleWhenDisabled() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.areConsoleMessagesEnabled()).thenReturn(false);

        consoleService.attach(configManager);
        consoleService.log(ConsoleMessage.ECONOMY_MISSING);

        verifyNoInteractions(logger);

    }
}
