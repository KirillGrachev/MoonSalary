package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    @Test
    @DisplayName("format_money: true форматирует сумму через экономику")
    void formatsMoneyThroughEconomy() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isMoneyFormattingEnabled()).thenReturn(true);

        EconomyService economyService = mock(EconomyService.class);
        when(economyService.format(100D)).thenReturn("$100.00");

        MessageService messageService = new MessageService(configManager, economyService,
                mock(ConsoleService.class), mock(PayoutSchedule.class));

        assertEquals("$100.00", messageService.formatMoney(100D));

    }

    @Test
    @DisplayName("format_money: false оставляет простое число")
    void keepsPlainNumber() {

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.isMoneyFormattingEnabled()).thenReturn(false);

        MessageService messageService = new MessageService(configManager, mock(EconomyService.class),
                mock(ConsoleService.class), mock(PayoutSchedule.class));

        assertEquals("100", messageService.formatMoney(100D));
        assertEquals("12.50", messageService.formatMoney(12.5D));

    }
}
