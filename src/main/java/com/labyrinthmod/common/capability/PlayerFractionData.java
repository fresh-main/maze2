package com.labyrinthmod.common.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

public class PlayerFractionData implements INBTSerializable<CompoundTag> {
    private FractionType fraction = FractionType.NONE;
    private String imposterMask = null; // Переименовано с traitorMask

    public FractionType getFraction() {
        return fraction;
    }

    public void setFraction(FractionType fraction) {
        this.fraction = fraction;
    }

    public String getImposterMask() {
        return imposterMask;
    }

    public void setImposterMask(String mask) {
        this.imposterMask = mask;
        if (fraction == FractionType.IMPOSTER && mask != null) {
            fraction.setMaskFraction(mask);
        }
    }

    public boolean hasImposterMask() {
        return imposterMask != null && !imposterMask.isEmpty();
    }

    public boolean hasFraction() {
        return fraction != null && fraction != FractionType.NONE;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("fraction", fraction.id);
        if (imposterMask != null) {
            tag.putString("imposterMask", imposterMask);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("fraction")) {
            fraction = FractionType.fromId(tag.getInt("fraction"));
        } else {
            fraction = FractionType.NONE;
        }
        if (tag.contains("imposterMask")) {
            imposterMask = tag.getString("imposterMask");
            if (fraction == FractionType.IMPOSTER) {
                fraction.setMaskFraction(imposterMask);
            }
        }
        // Для совместимости со старыми данными
        if (tag.contains("traitorMask")) {
            imposterMask = tag.getString("traitorMask");
            if (fraction == FractionType.IMPOSTER) {
                fraction.setMaskFraction(imposterMask);
            }
        }
    }
}