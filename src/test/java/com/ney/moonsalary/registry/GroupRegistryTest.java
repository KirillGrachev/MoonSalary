package com.ney.moonsalary.registry;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupRegistryTest {

    private GroupRegistry groupRegistry;

    @BeforeEach
    void setUp() {

        ConfigManager configManager = mock(ConfigManager.class);

        when(configManager.getGroups()).thenReturn(List.of(
                new SalaryGroupSettings("default", 100D, 0, List.of(), List.of()),
                new SalaryGroupSettings("moon", 900D, 7, List.of(), List.of()),
                new SalaryGroupSettings("staff", 500D, 21, List.of(), List.of("p give {player} 25"))
        ));

        this.groupRegistry = new GroupRegistry(configManager);

    }

    @Test
    @DisplayName("Все группы из конфигурации регистрируются")
    void registersAllGroups() {

        assertEquals(3, groupRegistry.getRegisteredGroups().size());
        assertTrue(groupRegistry.isGroupRegistered("moon"));
        assertTrue(groupRegistry.isGroupRegistered("MOON"));

    }

    @Test
    @DisplayName("Название группы не зависит от регистра")
    void resolvesGroupIgnoringCase() {

        assertEquals(900D, groupRegistry.getGroup("MoOn").getSalary());
        assertNull(groupRegistry.getGroup("unknown"));
        assertNull(groupRegistry.getGroup(null));

    }

    @Test
    @DisplayName("Выбирается группа с наивысшим приоритетом")
    void picksHighestPriorityGroup() {

        SalaryGroup group = groupRegistry.resolveGroup(
                List.of("group.default", "group.moon", "moon_salary.bypass.afk"), "group.");

        assertEquals("moon", group.getName());

    }

    @Test
    @DisplayName("Права без префикса группы игнорируются")
    void ignoresForeignPermissions() {

        assertNull(groupRegistry.resolveGroup(List.of("essentials.fly", "moon"), "group."));

    }

    @Test
    @DisplayName("Незарегистрированная группа в правах игнорируется")
    void ignoresUnknownGroupPermission() {

        SalaryGroup group = groupRegistry.resolveGroup(List.of("group.hero", "group.default"), "group.");

        assertEquals("default", group.getName());

    }

    @Test
    @DisplayName("Настраиваемый префикс права работает")
    void supportsCustomPrefix() {

        SalaryGroup group = groupRegistry.resolveGroup(List.of("rank.staff"), "rank.");

        assertEquals("staff", group.getName());

    }

    @Test
    @DisplayName("Список групп отсортирован по приоритету")
    void sortsGroupsByPriority() {

        List<String> names = groupRegistry.getRegisteredGroups().stream()
                .map(SalaryGroup::getName)
                .toList();

        assertEquals(List.of("default", "moon", "staff"), names);

    }

    @Test
    @DisplayName("reloadRegistry пересоздаёт реестр")
    void reloadsRegistry() {

        groupRegistry.reloadRegistry();

        assertEquals(3, groupRegistry.getRegisteredGroups().size());

    }
}
