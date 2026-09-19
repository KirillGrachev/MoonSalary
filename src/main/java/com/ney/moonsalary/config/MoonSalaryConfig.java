package com.ney.moonsalary.config;

import com.ney.moonsalary.config.type.PayoutMode;
import com.ney.moonsalary.config.type.SalaryGroupSettings;
import com.ney.moonsalary.config.type.SoundSettings;

import java.util.List;

/**
 * Контракт конфигурации плагина MoonSalary.
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

    PayoutMode getPayoutMode();

    String getFallbackGroup();

    long getSalaryIntervalTicks();

    long getSalaryIntervalSeconds();

    long getSalaryIntervalMillis();

    long getAfkCheckIntervalTicks();

    long getAfkThresholdMillis();

    int getTitleFadeIn();

    int getTitleStay();

    int getTitleFadeOut();

    SoundSettings getSalarySound();

    String getGroupPermissionPrefix();

    String getPermissionBypassAfk();

    String getPermissionReload();

    String getPermissionList();

    String getMessagePrefix();

    String getSalaryTitle();

    String getSalarySubtitle();

    String getBlockedAfkMessage();

    List<String> getInfoSelfMessage();

    List<String> getInfoOtherMessage();

    String getListHeader();

    String getListEntry();

    String getListEmpty();

    String getStatusOnline();

    String getStatusAfk();

    String getStatusOffline();

    String getStatusNoGroup();

    String getNoPermissionMessage();

    String getUsageMessage();

    String getUnknownPlayerMessage();

    String getReloadSuccessMessage();

    String getStartupFailedMessage();

    List<SalaryGroupSettings> getGroups();

}
