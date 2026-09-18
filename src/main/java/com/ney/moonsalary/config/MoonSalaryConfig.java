package com.ney.moonsalary.config;

import com.ney.moonsalary.config.type.AfkNotifyType;
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

    boolean areAfkNotificationsEnabled();

    boolean shouldRepeatAfkMessage();

    boolean areMessagesEnabled();

    boolean arePermissionsEnabled();

    long getSalaryIntervalTicks();

    long getSalaryIntervalSeconds();

    long getAfkCheckIntervalTicks();

    long getAfkThresholdMillis();

    int getTitleFadeIn();

    int getTitleStay();

    int getTitleFadeOut();

    AfkNotifyType getAfkNotifyType();

    SoundSettings getAfkSound();

    SoundSettings getSalarySound();

    String getGroupPermissionPrefix();

    String getPermissionBypassAfk();

    String getPermissionReload();

    String getPermissionList();

    String getMessagePrefix();

    String getSalaryTitle();

    String getSalarySubtitle();

    List<String> getAfkMessage();

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

    List<SalaryGroupSettings> getGroups();

}
