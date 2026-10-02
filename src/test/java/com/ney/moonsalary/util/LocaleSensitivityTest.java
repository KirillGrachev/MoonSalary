package com.ney.moonsalary.util;

import com.ney.moonsalary.command.SalaryCommand;
import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.registry.GroupRegistry;
import org.bukkit.Sound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Регрессии локалезависимости: сервер может стартовать с любой дефолтной
 * локалью JVM, а матчинг подкоманд, имён звуков, групп и форматирование
 * сумм обязаны вести себя одинаково везде (Locale.ROOT).
 */
class LocaleSensitivityTest {

    private static final Locale TURKISH = new Locale("tr", "TR");

    private Locale original;

    @BeforeEach
    void setUp() {
        original = Locale.getDefault();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(original);
    }

    @Test
    @DisplayName("Фильтр автодополнения не ломается турецкой локалью")
    void filterIsTurkishProof() {
        Locale.setDefault(TURKISH);
        assertEquals(List.of("info"), SalaryCommand.filter(List.of("info", "list"), "INFO"));
    }

    @Test
    @DisplayName("Имя звука с 'i' разрешается в турецкой локали")
    void soundNamesAreTurkishProof() {

        Locale.setDefault(TURKISH);
        SoundSettings settings = SoundSettings.of(
                "block_wooden_button_click_on", true, 1F, 1F, null);

        assertTrue(settings.isPlayable());
        assertEquals(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, settings.sound());

    }

    @Test
    @DisplayName("Название группы не зависит от регистра и локали")
    void groupNamesAreTurkishProof() {

        Locale.setDefault(TURKISH);
        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getGroups()).thenReturn(List.of(
                new SalaryGroupSettings("vip", 250D, 1, List.of(), List.of())));

        GroupRegistry registry = new GroupRegistry(configManager);

        assertNotNull(registry.getGroup("VIP"));
        assertNotNull(registry.getGroup("vip"));

    }

    @Test
    @DisplayName("Дробная сумма форматируется точкой в любой локали")
    void moneyFormattingIsLocaleProof() {

        Locale.setDefault(Locale.GERMANY);
        assertEquals("12.50", PlaceholderUtil.formatMoney(12.5D));

        Locale.setDefault(TURKISH);
        assertEquals("12.50", PlaceholderUtil.formatMoney(12.5D));

    }

}
