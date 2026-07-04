package com.infection.settings;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Настройки системы заражения. Хранятся на сервере (InfectionSavedData),
 * синхронизируются всем клиентам (ClientSettings) и редактируются из GUI только админом.
 */
public final class InfectionSettings {

    public int minLevel = 10;
    public int terminalStartLevel = 100;
    public int startIntervalTicks = 6000;
    public int terminalIntervalTicks = 80;
    public int reductionPerPercentTicks = 5;
    public int attackMinDurationTicks = 60;
    public int attackMaxDurationTicks = 110;
    /** Сколько тиков нужно для автоматического +1% к заражению (сервер-сайд). 0 = отключено. */
    public int growthIntervalTicks = 1200;
    /** Интервал (в тиках) между тиками терминального урона на 100% заражения. 0 = не умирать. */
    public int terminalDamageIntervalTicks = 300;
    /** С какого % заражения у игрока играет зацикленный infestation_ambience. 101+ = никогда.
     *  Громкость растёт линейно от ambienceStartLevel до ambienceFullLevel. */
    public int ambienceStartLevel = 60;
    /** На каком % громкость амбиента доходит до 1.0. */
    public int ambienceFullLevel = 90;

    public InfectionSettings copy() {
        InfectionSettings s = new InfectionSettings();
        s.minLevel = this.minLevel;
        s.terminalStartLevel = this.terminalStartLevel;
        s.startIntervalTicks = this.startIntervalTicks;
        s.terminalIntervalTicks = this.terminalIntervalTicks;
        s.reductionPerPercentTicks = this.reductionPerPercentTicks;
        s.attackMinDurationTicks = this.attackMinDurationTicks;
        s.attackMaxDurationTicks = this.attackMaxDurationTicks;
        s.growthIntervalTicks = this.growthIntervalTicks;
        s.terminalDamageIntervalTicks = this.terminalDamageIntervalTicks;
        s.ambienceStartLevel = this.ambienceStartLevel;
        s.ambienceFullLevel = this.ambienceFullLevel;
        return s;
    }

    public void clampAll() {
        minLevel = clamp(minLevel, 1, 100);
        terminalStartLevel = clamp(terminalStartLevel, 2, 100);
        startIntervalTicks = clamp(startIntervalTicks, 20, 72000);
        terminalIntervalTicks = clamp(terminalIntervalTicks, 10, 72000);
        reductionPerPercentTicks = clamp(reductionPerPercentTicks, 0, 72000);
        attackMinDurationTicks = clamp(attackMinDurationTicks, 10, 1200);
        attackMaxDurationTicks = clamp(attackMaxDurationTicks, attackMinDurationTicks + 1, 1200);
        growthIntervalTicks = clamp(growthIntervalTicks, 0, 432000); // 0 = off, max 6 часов
        terminalDamageIntervalTicks = clamp(terminalDamageIntervalTicks, 0, 72000); // 0 = не умирать
        ambienceStartLevel = clamp(ambienceStartLevel, 1, 101); // 101 = никогда
        ambienceFullLevel = clamp(ambienceFullLevel, ambienceStartLevel + 1, 101);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Линейный спад от уровня. Возвращает Integer.MAX_VALUE если уровень ниже minLevel. */
    public int computeIntervalTicks(int level) {
        if (level < minLevel) return Integer.MAX_VALUE;
        if (level >= terminalStartLevel) return terminalIntervalTicks;
        int val = startIntervalTicks - (level - minLevel) * reductionPerPercentTicks;
        return Math.max(terminalIntervalTicks, Math.min(startIntervalTicks, val));
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("min_level", minLevel);
        t.putInt("terminal_start_level", terminalStartLevel);
        t.putInt("start_interval", startIntervalTicks);
        t.putInt("terminal_interval", terminalIntervalTicks);
        t.putInt("reduction_per_percent", reductionPerPercentTicks);
        t.putInt("attack_min_dur", attackMinDurationTicks);
        t.putInt("attack_max_dur", attackMaxDurationTicks);
        t.putInt("growth_interval", growthIntervalTicks);
        t.putInt("terminal_damage_interval", terminalDamageIntervalTicks);
        t.putInt("ambience_start_level", ambienceStartLevel);
        t.putInt("ambience_full_level", ambienceFullLevel);
        return t;
    }

    public void load(CompoundTag t) {
        if (t.contains("min_level")) minLevel = t.getInt("min_level");
        if (t.contains("terminal_start_level")) terminalStartLevel = t.getInt("terminal_start_level");
        if (t.contains("start_interval")) startIntervalTicks = t.getInt("start_interval");
        if (t.contains("terminal_interval")) terminalIntervalTicks = t.getInt("terminal_interval");
        if (t.contains("reduction_per_percent")) reductionPerPercentTicks = t.getInt("reduction_per_percent");
        if (t.contains("attack_min_dur")) attackMinDurationTicks = t.getInt("attack_min_dur");
        if (t.contains("attack_max_dur")) attackMaxDurationTicks = t.getInt("attack_max_dur");
        if (t.contains("growth_interval")) growthIntervalTicks = t.getInt("growth_interval");
        if (t.contains("terminal_damage_interval")) terminalDamageIntervalTicks = t.getInt("terminal_damage_interval");
        if (t.contains("ambience_start_level")) ambienceStartLevel = t.getInt("ambience_start_level");
        if (t.contains("ambience_full_level")) ambienceFullLevel = t.getInt("ambience_full_level");
        clampAll();
    }

    public void writeTo(FriendlyByteBuf buf) {
        buf.writeVarInt(minLevel);
        buf.writeVarInt(terminalStartLevel);
        buf.writeVarInt(startIntervalTicks);
        buf.writeVarInt(terminalIntervalTicks);
        buf.writeVarInt(reductionPerPercentTicks);
        buf.writeVarInt(attackMinDurationTicks);
        buf.writeVarInt(attackMaxDurationTicks);
        buf.writeVarInt(growthIntervalTicks);
        buf.writeVarInt(terminalDamageIntervalTicks);
        buf.writeVarInt(ambienceStartLevel);
        buf.writeVarInt(ambienceFullLevel);
    }

    public static InfectionSettings readFrom(FriendlyByteBuf buf) {
        InfectionSettings s = new InfectionSettings();
        s.minLevel = buf.readVarInt();
        s.terminalStartLevel = buf.readVarInt();
        s.startIntervalTicks = buf.readVarInt();
        s.terminalIntervalTicks = buf.readVarInt();
        s.reductionPerPercentTicks = buf.readVarInt();
        s.attackMinDurationTicks = buf.readVarInt();
        s.attackMaxDurationTicks = buf.readVarInt();
        s.growthIntervalTicks = buf.readVarInt();
        s.terminalDamageIntervalTicks = buf.readVarInt();
        s.ambienceStartLevel = buf.readVarInt();
        s.ambienceFullLevel = buf.readVarInt();
        s.clampAll();
        return s;
    }
}
