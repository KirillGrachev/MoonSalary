package com.ney.moonsalary.config;

import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.config.type.SoundSettings;
import com.ney.moonsalary.config.type.StorageSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Контракт конфигурации плагина MoonSalary.
 * <p>
 * Сервисы зависят от этого интерфейса, а не от конкретной реализации:
 * поведение конфигурации можно подменить в тестах.
 */
public interface MoonSalaryConfig {

    boolean isEnabled();

    boolean areCommandsEnabled();

    boolean isMoneyFormattingEnabled();

    boolean isTitleEnabled();

    boolean isAfkEnabled();

    boolean isAfkRotationIgnored();

    boolean areMessagesEnabled();

    boolean arePermissionsEnabled();

    @NotNull PayoutMode getPayoutMode();

    /**
     * PERSONAL-режим: не считать AFK-время в отсчёте до выплаты -
     * дедлайн ставится на паузу, пока игрок помечен AFK.
     *
     * @return true если простой в AFK не приближает выплату
     */
    boolean isPayoutPausedWhileAfk();

    @NotNull String getFallbackGroup();

    long getSalaryIntervalTicks();

    long getSalaryIntervalSeconds();

    long getSalaryIntervalMillis();

    long getAfkCheckIntervalTicks();

    long getAfkThresholdMillis();

    int getTitleFadeIn();

    int getTitleStay();

    int getTitleFadeOut();

    @NotNull SoundSettings getSalarySound();

    @NotNull String getGroupPermissionPrefix();

    @NotNull String getPermissionBypassAfk();

    @NotNull String getPermissionReload();

    @NotNull String getPermissionList();

    @NotNull String getPermissionInfo();

    @NotNull String getPermissionGive();

    @NotNull StorageSettings getStorageSettings();

    boolean isDownloadLibrariesEnabled();

    /**
     * Сколько записей истории выплат хранить на игрока (0 - история выключена).
     *
     * @return лимит записей
     */
    int getHistoryLimit();

    @NotNull String getMessagePrefix();

    @NotNull String getSalaryTitle();

    @NotNull String getSalarySubtitle();

    @NotNull String getBlockedAfkMessage();

    @NotNull List<String> getInfoSelfMessage();

    @NotNull List<String> getInfoOtherMessage();

    @NotNull String getListHeader();

    @NotNull String getListEntry();

    @NotNull String getListEmpty();

    @NotNull String getStatusOnline();

    @NotNull String getStatusAfk();

    @NotNull String getStatusNoGroup();

    /**
     * Текст для {last_payout}, когда истории выплат ещё нет.
     *
     * @return строка статуса
     */
    @NotNull String getStatusNoPayout();

    @NotNull String getNoPermissionMessage();

    @NotNull String getUsageMessage();

    @NotNull String getUnknownPlayerMessage();

    @NotNull String getReloadSuccessMessage();

    @NotNull String getGiveUsageMessage();

    @NotNull String getGiveSuccessMessage();

    @NotNull String getGiveFailedMessage();

    @NotNull String getGiveUnknownGroupMessage();

    @NotNull List<SalaryGroupSettings> getGroups();

}
