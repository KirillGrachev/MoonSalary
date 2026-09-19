package com.ney.moonsalary.registry;

import com.ney.moonsalary.config.ConfigManager;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupRegistry {

    private final ConfigManager configManager;
    private final Map<String, SalaryGroup> registeredGroups = new ConcurrentHashMap<>();

    public GroupRegistry(ConfigManager configManager) {
        this.configManager = configManager;
        initializeRegisteredGroups();
    }

    private void initializeRegisteredGroups() {
        configManager.getGroups().forEach(this::registerGroup);
    }

    private void registerGroup(@NotNull SalaryGroupSettings settings) {
        registeredGroups.put(normalizeGroupName(settings.name()), new SalaryGroup(settings));
    }

    private @NotNull String normalizeGroupName(@NotNull String name) {
        return name.toLowerCase();
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

        return resolveGroup(permissions, configManager.getGroupPermissionPrefix());

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
                .filter(group -> group != null)
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
        registeredGroups.clear();
    }

    public void reloadRegistry() {
        clearRegisteredGroups();
        initializeRegisteredGroups();
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
