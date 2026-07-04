package com.labyrinthmod.common.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

public class PossessionData implements INBTSerializable<CompoundTag> {
    private boolean isPossessing = false;
    private int possessedEntityId = -1;

    public boolean isPossessing() {
        return isPossessing;
    }

    public void setPossessing(boolean possessing) {
        isPossessing = possessing;
    }

    public int getPossessedEntityId() {
        return possessedEntityId;
    }

    public void setPossessedEntityId(int id) {
        this.possessedEntityId = id;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isPossessing", isPossessing);
        tag.putInt("possessedEntityId", possessedEntityId);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        isPossessing = tag.getBoolean("isPossessing");
        possessedEntityId = tag.getInt("possessedEntityId");
    }
}