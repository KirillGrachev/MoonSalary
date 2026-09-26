package com.ney.moonsalary.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Разрешает звук по имени из конфигурации.
 * <p>
 * На ядрах 1.21.3+ звуки живут в {@code Registry.SOUNDS} (а сам Sound стал
 * интерфейсом), на более старых - только enum {@link Sound}. Резолвер
 * проверяет оба пути через рефлексию там, где API может отсутствовать,
 * поэтому один и тот же jar работает на всём диапазоне поддерживаемых ядер
 * независимо от того, против какой версии API он скомпилирован.
 * <p>
 * Регистр приводится через {@link Locale#ROOT}: на серверах с локалью вроде
 * турецкой "i" при toUpperCase превращается в "İ" и ломает поиск по имени.
 */
public final class SoundResolver {

    private SoundResolver() {
    }

    /**
     * Ищет звук по имени (enum-константа или ключ реестра, регистр не важен).
     * <p>
     * Порядок: сначала реестр (на ядрах 1.21.3+ он есть всегда и не зависит
     * от enum/interface-природы Sound в конкретном API), затем enum-фолбэк
     * для старых ядер.
     *
     * @param name имя звука из конфигурации
     * @return звук или null, если имя не распознано
     */
    public static @Nullable Sound resolve(@NotNull String name) {

        String key = name.trim();

        if (key.isEmpty()) {
            return null;
        }

        Sound fromRegistry = fromRegistry(key);
        return fromRegistry != null ? fromRegistry : fromEnum(key);

    }

    /**
     * Основной путь - enum {@link Sound} (ядра до 1.21.2 и весь Spigot).
     * <p>
     * На Paper 1.21.3+ Sound стал интерфейсом, и valueOf, скомпилированный
     * против enum, может бросить {@link LinkageError} (несовместимость
     * IncompatibleClassChangeError) - оно перехватывается, и разрешение
     * продолжается через реестр звуков.
     */
    private static @Nullable Sound fromEnum(@NotNull String name) {
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | LinkageError exception) {
            return null;
        }
    }

    /**
     * Путь для ядер, где звуки перенесены в Registry (1.21.3+):
     * ищем {@code Registry.SOUNDS.get(NamespacedKey)} через рефлексию.
     * На старых ядрах поля SOUNDS нет - возвращаем null, и работает enum-путь.
     *
     * @param name имя звука (ключ реестра в нижнем регистре)
     * @return звук или null, если реестра нет или имя не найдено
     */
    private static @Nullable Sound fromRegistry(@NotNull String name) {

        try {

            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Field soundsField = registryClass.getField("SOUNDS");
            Object sounds = soundsField.get(null);

            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            if (key == null) {
                return null;
            }

            Method get = registryClass.getMethod("get", NamespacedKey.class);
            Object sound = get.invoke(sounds, key);

            return sound instanceof Sound resolved ? resolved : null;

        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // реестра звуков на этом ядре нет или имя не найдено -
            // вызывающий код предупредит о неизвестном звуке
            return null;
        }

    }

}
