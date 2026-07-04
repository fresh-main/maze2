package com.infection.capability;

public enum InfectionStage {
    CLEAN(0),
    TRACE(1),       // 1..19 — еле заметно
    EARLY(20),      // 20..39
    ACTIVE(40),     // 40..59
    HEAVY(60),      // 60..79
    CRITICAL(80),   // 80..99
    TERMINAL(100);  // 100 — полная трансформация

    public final int threshold;

    InfectionStage(int threshold) {
        this.threshold = threshold;
    }

    public static InfectionStage fromLevel(int level) {
        if (level <= 0) return CLEAN;
        if (level >= 100) return TERMINAL;
        if (level >= 80) return CRITICAL;
        if (level >= 60) return HEAVY;
        if (level >= 40) return ACTIVE;
        if (level >= 20) return EARLY;
        return TRACE;
    }

    /** Непрозрачность чёрного оверлея на модельке, 0..1. */
    public float overlayAlpha() {
        return switch (this) {
            case CLEAN -> 0f;
            case TRACE -> 0.12f;
            case EARLY -> 0.28f;
            case ACTIVE -> 0.45f;
            case HEAVY -> 0.62f;
            case CRITICAL -> 0.80f;
            case TERMINAL -> 0.94f;
        };
    }
}
