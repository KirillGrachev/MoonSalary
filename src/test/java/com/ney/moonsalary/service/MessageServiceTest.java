package com.ney.moonsalary.service;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.ConsoleMessage;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.storage.PayoutEntry;
import com.ney.moonsalary.storage.PayoutHistoryService;
import com.ney.moonsalary.storage.PayoutOutcome;
import com.ney.moonsalary.storage.PayoutSource;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageServiceTest {

    private ConfigManager configManager;
    private ConsoleService consoleService;
    private PayoutHistoryService historyService;
    private MessageService messageService;

    @BeforeEach
    void setUp() {

        configManager = mock(ConfigManager.class);
        consoleService = mock(ConsoleService.class);
        historyService = mock(PayoutHistoryService.class);
        when(configManager.getMessagePrefix()).thenReturn("P>");
        when(configManager.getSalaryIntervalSeconds()).thenReturn(60L);
        when(configManager.getStatusNoPayout()).thenReturn("never");
        this.messageService = new MessageService(configManager, mock(EconomyService.class),
                consoleService, mock(PayoutSchedule.class), historyService);

    }

    @Test
    @DisplayName("sendLine подставляет {prefix} и {player}")
    void sendLineSubstitutesTokens() {

        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Ney");
        messageService.sendLine(sender, "{prefix}{player} hi");

        verify(sender).sendMessage("P>Ney hi");

    }

    @Test
    @DisplayName("sendLine не отправляет пустые строки")
    void sendLineSkipsEmpty() {

        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Ney");
        messageService.sendLine(sender, "");

        verify(sender, never()).sendMessage(anyString());

    }

    @Test
    @DisplayName("Префикс приходит только туда, где шаблон его запросил")
    void prefixOnlyWhereRequested() {

        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Ney");
        messageService.sendFormatted(sender, null, List.of("{prefix}one", "two"), null, 0D, "ok");

        verify(sender).sendMessage("P>one");
        verify(sender).sendMessage("two");

    }

    @Test
    @DisplayName("Предупреждение об отсутствии PlaceholderAPI выводится один раз")
    void missingPlaceholdersWarnedOnce() {

        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Ney");
        messageService.sendLine(sender, "%foo%");
        messageService.sendLine(sender, "%foo%");

        verify(consoleService, times(1)).log(ConsoleMessage.PLACEHOLDERS_NO_API);

    }

    @Test
    @DisplayName("Пустой тайтл не отправляется")
    void emptyTitleSkipped() {

        Player player = mock(Player.class);
        messageService.sendTitle(player, "", "");

        verifyNoInteractions(player);

    }

    @Test
    @DisplayName("Непроигрываемый звук не отправляется")
    void unplayableSoundSkipped() {

        Player player = mock(Player.class);
        messageService.playSound(player, new SoundSettings(false, Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1F));
        messageService.playSound(player, new SoundSettings(true, null, 1F, 1F));

        verifyNoInteractions(player);

    }

    @Test
    @DisplayName("Проигрываемый звук доходит до игрока")
    void playableSoundReachesPlayer() {

        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(mock(Location.class));
        messageService.playSound(player, new SoundSettings(true, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1F));

        verify(player).playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1F);

    }

    @Test
    @DisplayName("format_money управляет форматом суммы")
    void formatMoneySwitch() {

        EconomyService economyService = mock(EconomyService.class);
        when(economyService.format(100D)).thenReturn("$100.00");
        when(configManager.isMoneyFormattingEnabled()).thenReturn(true);
        MessageService withEconomy = new MessageService(configManager, economyService,
                consoleService, mock(PayoutSchedule.class), historyService);

        assertEquals("$100.00", withEconomy.formatMoney(100D));
        when(configManager.isMoneyFormattingEnabled()).thenReturn(false);

        assertEquals("100", withEconomy.formatMoney(100D));
        assertEquals("12.50", withEconomy.formatMoney(12.5D));

    }

    @Test
    @DisplayName("{last_payout} берётся из истории, без истории - статус no_payout")
    void lastPayoutPlaceholder() {

        java.util.UUID playerId = java.util.UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Ney");

        when(historyService.last(playerId)).thenReturn(java.util.Optional.empty());

        assertEquals("never", messageService.formatLine(player, player.getName(),
                "{last_payout}", null, 0D, "online"));

        PayoutEntry entry = new PayoutEntry(playerId, "Ney", 0L, 250D, "vip", PayoutOutcome.PAID, PayoutSource.SCHEDULED);
        when(historyService.last(playerId)).thenReturn(java.util.Optional.of(entry));
        String formatted = messageService.formatLine(player, player.getName(),
                "{last_payout}", null, 0D, "online");

        assertTrue(formatted.startsWith("250 ("));

    }

}
