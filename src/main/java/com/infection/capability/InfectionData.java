package com.infection.capability;

import com.infection.network.Network;
import com.infection.network.packet.S2CInfectionSyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

public class InfectionData implements IInfection {

    private int level = 0;
    private String noteText = "";
    private String customNoteText = "";
    private long growthSlowdownUntil = 0L;
    private long hallucinationsSuppressedUntil = 0L;
    private int lastAutoNoteStageOrdinal = -1;
    private int lastAutoNoteBucket = -1;
    private float growthMultiplier = 1.0f;
    /**
     * Аккумулятор для дробных множителей. Каждый тик прибавляем growthMultiplier;
     * как только overflow >= growthIntervalTicks — инкрементируем уровень и
     * вычитаем интервал из аккумулятора.
     */
    private float growthAccumulator = 0.0f;
    /** Персональный override интервала роста; 0 = использовать глобальный. */
    private int personalGrowthIntervalTicks = 0;

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void setLevel(int level) {
        this.level = Math.max(0, Math.min(100, level));
    }

    @Override
    public String getNoteText() {
        return noteText == null ? "" : noteText;
    }

    @Override
    public void setNoteText(String text) {
        this.noteText = text == null ? "" : text;
    }

    @Override
    public String getCustomNoteText() {
        return customNoteText == null ? "" : customNoteText;
    }

    @Override
    public void setCustomNoteText(String text) {
        this.customNoteText = text == null ? "" : text;
    }

    @Override
    public long getGrowthSlowdownUntil() {
        return growthSlowdownUntil;
    }

    @Override
    public void setGrowthSlowdownUntil(long t) {
        this.growthSlowdownUntil = t;
    }

    @Override
    public long getHallucinationsSuppressedUntil() {
        return hallucinationsSuppressedUntil;
    }

    @Override
    public void setHallucinationsSuppressedUntil(long t) {
        this.hallucinationsSuppressedUntil = t;
    }

    @Override
    public int getLastAutoNoteStageOrdinal() {
        return lastAutoNoteStageOrdinal;
    }

    @Override
    public void setLastAutoNoteStageOrdinal(int ordinal) {
        this.lastAutoNoteStageOrdinal = ordinal;
    }

    @Override
    public int getLastAutoNoteBucket() {
        return lastAutoNoteBucket;
    }

    @Override
    public void setLastAutoNoteBucket(int bucket) {
        this.lastAutoNoteBucket = bucket;
    }

    @Override
    public float getGrowthMultiplier() {
        return growthMultiplier;
    }

    @Override
    public void setGrowthMultiplier(float multiplier) {
        if (Float.isNaN(multiplier) || multiplier < 0f) multiplier = 0f;
        if (multiplier > 1000f) multiplier = 1000f;
        this.growthMultiplier = multiplier;
        if (multiplier == 0f) this.growthAccumulator = 0f;
    }

    /** Внутренние методы для аккумулятора — наружу не выставлены, используются только в InfectionMod tick. */
    public float getGrowthAccumulator() {
        return growthAccumulator;
    }

    public void setGrowthAccumulator(float v) {
        this.growthAccumulator = v;
    }

    @Override
    public int getPersonalGrowthIntervalTicks() {
        return personalGrowthIntervalTicks;
    }

    @Override
    public void setPersonalGrowthIntervalTicks(int ticks) {
        this.personalGrowthIntervalTicks = Math.max(0, Math.min(432000, ticks));
        // Сбрасываем аккумулятор, чтобы не «накопилось» предыдущим интервалом —
        // новый интервал должен начать с чистого листа.
        this.growthAccumulator = 0f;
    }

    @Override
    public void copyFrom(IInfection other) {
        this.level = other.getLevel();
        this.noteText = other.getNoteText();
        this.customNoteText = other.getCustomNoteText();
        this.growthSlowdownUntil = other.getGrowthSlowdownUntil();
        this.hallucinationsSuppressedUntil = other.getHallucinationsSuppressedUntil();
        this.lastAutoNoteStageOrdinal = other.getLastAutoNoteStageOrdinal();
        this.lastAutoNoteBucket = other.getLastAutoNoteBucket();
        this.growthMultiplier = other.getGrowthMultiplier();
        this.personalGrowthIntervalTicks = other.getPersonalGrowthIntervalTicks();
        if (other instanceof InfectionData d) this.growthAccumulator = d.growthAccumulator;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", level);
        tag.putString("note", noteText == null ? "" : noteText);
        tag.putString("custom_note", customNoteText == null ? "" : customNoteText);
        tag.putLong("growth_slowdown_until", growthSlowdownUntil);
        tag.putLong("hallu_suppressed_until", hallucinationsSuppressedUntil);
        tag.putInt("last_auto_stage", lastAutoNoteStageOrdinal);
        tag.putInt("last_auto_bucket", lastAutoNoteBucket);
        tag.putFloat("growth_multiplier", growthMultiplier);
        tag.putFloat("growth_accumulator", growthAccumulator);
        tag.putInt("personal_growth_interval", personalGrowthIntervalTicks);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        this.level = tag.getInt("level");
        this.noteText = tag.contains("note") ? tag.getString("note") : "";
        this.customNoteText = tag.contains("custom_note") ? tag.getString("custom_note") : "";
        this.growthSlowdownUntil = tag.contains("growth_slowdown_until") ? tag.getLong("growth_slowdown_until") : 0L;
        this.hallucinationsSuppressedUntil = tag.contains("hallu_suppressed_until") ? tag.getLong("hallu_suppressed_until") : 0L;
        this.lastAutoNoteStageOrdinal = tag.contains("last_auto_stage") ? tag.getInt("last_auto_stage") : -1;
        this.lastAutoNoteBucket = tag.contains("last_auto_bucket") ? tag.getInt("last_auto_bucket") : -1;
        this.growthMultiplier = tag.contains("growth_multiplier") ? tag.getFloat("growth_multiplier") : 1.0f;
        this.growthAccumulator = tag.contains("growth_accumulator") ? tag.getFloat("growth_accumulator") : 0.0f;
        this.personalGrowthIntervalTicks = tag.contains("personal_growth_interval") ? tag.getInt("personal_growth_interval") : 0;
    }

    @Override
    public void syncTo(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        S2CInfectionSyncPacket pkt = new S2CInfectionSyncPacket(
                sp.getUUID(), level, getNoteText(), getCustomNoteText(), hallucinationsSuppressedUntil,
                growthMultiplier, personalGrowthIntervalTicks);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
        Network.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> sp), pkt);
    }
}
