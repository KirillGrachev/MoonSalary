package com.ney.moonsalary.config.type;

/**
 * Способ уведомления игрока о том, что он ушёл в AFK.
 */
public enum AfkNotifyType {

    /** Не уведомлять игрока */
    NONE,

    /** Отправить сообщение в чат */
    CHAT,

    /** Отправить сообщение в чат и показать тайтл */
    CHAT_TITLE,

    /** Показать только тайтл */
    TITLE
}
