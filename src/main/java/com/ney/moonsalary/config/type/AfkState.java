package com.ney.moonsalary.config.type;

/**
 * Состояние игрока относительно AFK-защиты.
 */
public enum AfkState {

    /** Игрок активен, зарплата выдаётся */
    ACTIVE,

    /** Игрок в AFK, но имеет право обхода (bypass.afk) */
    BYPASSED,

    /** Игрок в AFK, зарплата не выдаётся */
    AFK
}
