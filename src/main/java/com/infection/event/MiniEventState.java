package com.infection.event;

/**
 * Фазы инвентика.
 *  IDLE       — не запущен.
 *  PREPARING  — админ выбрал инвентик, на него натянут чёрный силуэт. Без вспышки/звука.
 *               Админ может позиционироваться. Хоткей LAUNCH запускает фазу ACTIVE.
 *               Хоткей CANCEL возвращает в IDLE.
 *  ACTIVE     — финальная фаза: звук, вспышка экрана, поворот к цели. Через DURATION_TICKS
 *               админ рассеивается дымом → IDLE.
 */
public enum MiniEventState {
    IDLE,
    PREPARING,
    ACTIVE;

    public static MiniEventState byOrdinal(int o) {
        MiniEventState[] all = values();
        if (o < 0 || o >= all.length) return IDLE;
        return all[o];
    }
}
