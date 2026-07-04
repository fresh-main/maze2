package com.infection.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

public interface IInfection {
    /** 0..100 */
    int getLevel();

    void setLevel(int level);

    default InfectionStage stage() {
        return InfectionStage.fromLevel(getLevel());
    }

    /** Авто-самочувствие по стадии заражения. Сервер обновляет на пересечении стадии. */
    String getNoteText();

    void setNoteText(String text);

    /** Произвольный текст, который пишет админ через админ-меню или команду. Авто-генератор НЕ трогает. */
    String getCustomNoteText();

    void setCustomNoteText(String text);

    /** Game time (тики), до которого рост заражения замедлен ×0.5. */
    long getGrowthSlowdownUntil();

    void setGrowthSlowdownUntil(long gameTimeTicks);

    /** Game time (тики), до которого приступы галлюцинаций подавлены. */
    long getHallucinationsSuppressedUntil();

    void setHallucinationsSuppressedUntil(long gameTimeTicks);

    /** Стадия, на которой записка была сгенерирована автоматически в последний раз. */
    int getLastAutoNoteStageOrdinal();

    void setLastAutoNoteStageOrdinal(int ordinal);

    /** 5%-бакет (0..20), на котором был сгенерирован авто-текст в последний раз. */
    int getLastAutoNoteBucket();

    void setLastAutoNoteBucket(int bucket);

    /**
     * Множитель скорости роста заражения для конкретного игрока.
     * 0 = рост остановлен; 1 = базовая скорость (settings.growthIntervalTicks);
     * >1 — быстрее (10 ≈ в 10 раз быстрее, 30 = «всё за пару секунд»).
     * По умолчанию 1.0.
     */
    float getGrowthMultiplier();

    void setGrowthMultiplier(float multiplier);

    /**
     * Персональный override интервала роста (в тиках) для конкретного игрока.
     * 0 = использовать глобальный settings.growthIntervalTicks.
     * Иначе — этот интервал заменяет глобальный для расчёта прироста заражения.
     * Это позволяет админу ставить РАЗНУЮ скорость заражения каждому игроку
     * не трогая глобальные настройки.
     */
    int getPersonalGrowthIntervalTicks();

    void setPersonalGrowthIntervalTicks(int ticks);

    void copyFrom(IInfection other);

    CompoundTag save();

    void load(CompoundTag tag);

    /** Рассылает S2C-пакет владельцу и трекерам (только на сервере). */
    void syncTo(Player player);
}
