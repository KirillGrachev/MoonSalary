package com.ney.moonsalary.registry;

import com.ney.moonsalary.config.MoonSalaryConfig;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Реестр групп зарплат.
 * <p>
 * Все вызовы из основного потока сервера. reload собирает новую карту
 * и подменяет ссылку одной операцией: читатели никогда не видят
 * наполовину заполненный реестр.
 */
public class GroupRegistry {

    private final MoonSalaryConfig configManager;

    private Map<String, SalaryGroup> registeredGroups = new HashMap<>();

    public GroupRegistry(@NotNull MoonSalaryConfig configManager) {
        this.configManager = configManager;
        this.registeredGroups = buildRegistry();
    }

    private @NotNull Map<String, SalaryGroup> buildRegistry() {

        Map<String, SalaryGroup> rebuilt = new HashMap<>();

        for (SalaryGroupSettings settings : configManager.getGroups()) {
            rebuilt.put(normalizeGroupName(settings.name()), new SalaryGroup(settings));
        }

        return rebuilt;

    }

    private @NotNull String normalizeGroupName(@NotNull String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /**
     * Возвращает группу по её названию.
     *
     * @param groupName название группы
     * @return группа или null если она не зарегистрирована
     */
    public @Nullable SalaryGroup getGroup(@Nullable String groupName) {

        if (groupName == null || groupName.isEmpty()) {
            return null;
        }

        return registeredGroups.get(normalizeGroupName(groupName));

    }

    /**
     * Ищет группу игрока с наивысшим приоритетом.
     * Группа определяется по праву вида <prefix><group> (например, group.moon).
     *
     * @param player игрок
     * @return группа с наибольшим приоритетом или null
     */
    public @Nullable SalaryGroup getPlayerGroup(@NotNull Player player) {

        Collection<String> permissions = player.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .toList();

        SalaryGroup group = resolveGroup(permissions, configManager.getGroupPermissionPrefix());
        return group != null ? group : getFallbackGroup();

    }

    /**
     * Возвращает fallback-группу для игроков без групповых прав
     * (например, когда permission-плагин не установлен).
     *
     * @return fallback-группа или null если fallback выключен
     */
    private @Nullable SalaryGroup getFallbackGroup() {

        String fallbackName = configManager.getFallbackGroup();

        if (fallbackName.isEmpty()) {
            return null;
        }

        return getGroup(fallbackName);

    }

    /**
     * Выбирает группу с наивысшим приоритетом из списка прав.
     *
     * @param permissions права игрока
     * @param prefix      префикс права группы (например, "group.")
     * @return подходящая группа или null
     */
    public @Nullable SalaryGroup resolveGroup(@NotNull Collection<String> permissions,
                                              @NotNull String prefix) {
        return permissions.stream()
                .filter(permission -> permission.startsWith(prefix))
                .map(permission -> getGroup(permission.substring(prefix.length())))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(SalaryGroup::getPriority))
                .orElse(null);
    }

    /**
     * Проверяет, зарегистрирована ли группа.
     *
     * @param groupName название группы
     * @return true если группа зарегистрирована
     */
    public boolean isGroupRegistered(@Nullable String groupName) {
        return getGroup(groupName) != null;
    }

    public void clearRegisteredGroups() {
        registeredGroups = new HashMap<>();
    }

    /**
     * Пересобирает реестр из конфигурации атомарной подменой карты.
     */
    public void reloadRegistry() {
        registeredGroups = buildRegistry();
    }

    /**
     * Возвращает все группы, отсортированные по приоритету (по возрастанию).
     *
     * @return отсортированный список групп
     */
    public @NotNull List<SalaryGroup> getRegisteredGroups() {
        return registeredGroups.values().stream()
                .sorted(Comparator.comparingInt(SalaryGroup::getPriority)
                        .thenComparing(SalaryGroup::getName))
                .toList();
    }

}
