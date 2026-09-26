package com.ney.moonsalary.storage;

/**
 * Исход попытки выплаты: хранится в истории зарплат.
 */
public enum PayoutOutcome {

    /** Деньги выданы. */
    PAID,

    /** Выплата заблокирована AFK-защитой. */
    BLOCKED_AFK,

    /** Выплата отменена другим плагином через SalaryPayEvent. */
    CANCELLED,

    /** Экономика отклонила операцию. */
    FAILED

}
