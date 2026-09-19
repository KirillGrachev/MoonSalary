package com.ney.moonsalary.config.type;

/**
 * Режим выдачи зарплат.
 */
public enum PayoutMode {

    /** Payday: все игроки получают зарплату одновременно каждые N секунд */
    GLOBAL,

    /** Личный таймер: отсчёт начинается с момента захода игрока на сервер */
    PERSONAL
}
