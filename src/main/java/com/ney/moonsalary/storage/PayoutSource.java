package com.ney.moonsalary.storage;

/**
 * Источник выплаты в истории: плановая по расписанию или ручная командой.
 */
public enum PayoutSource {

    /** Выплата по расписанию (GLOBAL payday или персональное окно). */
    SCHEDULED,

    /** Ручная выплата командой /salary give. */
    MANUAL

}
