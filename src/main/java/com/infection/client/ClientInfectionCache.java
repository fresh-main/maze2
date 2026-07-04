package com.infection.client;

import com.infection.capability.InfectionStage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентский кеш заражения по UUID игрока. Обновляется серверным сайнк-пакетом.
 * Хранит уровень, авто-текст записки (по стадии), кастомный текст (от админа),
 * tick-маркер подавления галлюцинаций и индивидуальный множитель скорости роста.
 */
public final class ClientInfectionCache {

    public record Entry(int level, String noteText, String customNoteText,
                        long hallucinationsSuppressedUntil, float growthMultiplier,
                        int personalGrowthIntervalTicks) {
        public static final Entry EMPTY = new Entry(0, "", "", 0L, 1.0f, 0);
    }

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private ClientInfectionCache() {}

    public static void put(UUID id, int level, String noteText, String customNoteText,
                           long hallucinationsSuppressedUntil, float growthMultiplier,
                           int personalGrowthIntervalTicks) {
        boolean noData = level <= 0
                && (noteText == null || noteText.isEmpty())
                && (customNoteText == null || customNoteText.isEmpty())
                && hallucinationsSuppressedUntil == 0L
                && growthMultiplier == 1.0f
                && personalGrowthIntervalTicks == 0;
        if (noData) {
            ENTRIES.remove(id);
        } else {
            ENTRIES.put(id, new Entry(
                    Math.max(0, Math.min(100, level)),
                    noteText == null ? "" : noteText,
                    customNoteText == null ? "" : customNoteText,
                    hallucinationsSuppressedUntil,
                    growthMultiplier,
                    Math.max(0, personalGrowthIntervalTicks)));
        }
    }

    /** Старый overload без personal interval — сохраняет предыдущее значение. */
    public static void put(UUID id, int level, String noteText, String customNoteText,
                           long hallucinationsSuppressedUntil, float growthMultiplier) {
        Entry prev = ENTRIES.getOrDefault(id, Entry.EMPTY);
        put(id, level, noteText, customNoteText, hallucinationsSuppressedUntil,
                growthMultiplier, prev.personalGrowthIntervalTicks);
    }

    public static void put(UUID id, int level, String noteText, String customNoteText,
                           long hallucinationsSuppressedUntil) {
        Entry prev = ENTRIES.getOrDefault(id, Entry.EMPTY);
        put(id, level, noteText, customNoteText, hallucinationsSuppressedUntil,
                prev.growthMultiplier, prev.personalGrowthIntervalTicks);
    }

    public static void put(UUID id, int level) {
        Entry prev = ENTRIES.getOrDefault(id, Entry.EMPTY);
        put(id, level, prev.noteText, prev.customNoteText, prev.hallucinationsSuppressedUntil,
                prev.growthMultiplier, prev.personalGrowthIntervalTicks);
    }

    public static Entry getEntry(UUID id) {
        return ENTRIES.getOrDefault(id, Entry.EMPTY);
    }

    public static int get(UUID id) {
        return getEntry(id).level();
    }

    public static String getNoteText(UUID id) {
        return getEntry(id).noteText();
    }

    public static String getCustomNoteText(UUID id) {
        return getEntry(id).customNoteText();
    }

    public static float getGrowthMultiplier(UUID id) {
        return getEntry(id).growthMultiplier();
    }

    public static boolean isHallucinationsSuppressed(UUID id, long currentGameTime) {
        return getEntry(id).hallucinationsSuppressedUntil() > currentGameTime;
    }

    public static InfectionStage stage(UUID id) {
        return InfectionStage.fromLevel(get(id));
    }

    public static void clear() {
        ENTRIES.clear();
    }
}
