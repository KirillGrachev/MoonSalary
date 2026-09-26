package com.ney.moonsalary.config.type;

import com.ney.moonsalary.util.SoundResolver;
import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Настройки звука из конфигурации.
 *
 * @param enabled включён ли звук
 * @param sound   bukkit-звук (null если звук отключён или указан неверно)
 * @param volume  громкость
 * @param pitch   высота звука
 */
public record SoundSettings(boolean enabled,
                            @Nullable Sound sound,
                            float volume,
                            float pitch) {

    /**
     * Создаёт настройки звука из имени в конфигурации.
     * Имя разрешается через {@link SoundResolver}: enum-константа либо
     * ключ реестра звуков (ядра 1.21.3+), регистр и локаль не важны.
     *
     * @param name      имя bukkit-звука
     * @param enabled   включён ли звук
     * @param volume    громкость
     * @param pitch     высота звука
     * @param onInvalid вызывается если имя звука не распознано
     * @return готовые настройки звука
     */
    public static @NotNull SoundSettings of(@Nullable String name,
                                            boolean enabled,
                                            float volume,
                                            float pitch,
                                            @Nullable Runnable onInvalid) {

        if (!enabled || name == null || name.isBlank()) {
            return new SoundSettings(false, null, volume, pitch);
        }

        Sound resolved = SoundResolver.resolve(name);

        if (resolved == null) {

            if (onInvalid != null) {
                onInvalid.run();
            }

            return new SoundSettings(false, null, volume, pitch);

        }

        return new SoundSettings(true, resolved, volume, pitch);

    }

    /**
     * Проверяет, можно ли проиграть этот звук.
     *
     * @return true если звук включён и корректен
     */
    public boolean isPlayable() {
        return enabled && sound != null;
    }

}
