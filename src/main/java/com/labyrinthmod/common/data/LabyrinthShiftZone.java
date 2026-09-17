package com.labyrinthmod.common.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

import java.util.UUID;

public class LabyrinthShiftZone {

    public final UUID id;
    public BlockPos minPos;
    public BlockPos maxPos;

    public final int offsetX;
    public final int offsetY;
    public final int offsetZ;

    public boolean isVariantB;

    // НОВОЕ ПОЛЕ: UUID сущности-контрапции, когда она будет создана
    public UUID contraptionEntityId;

    public LabyrinthShiftZone(UUID id, BlockPos minPos, BlockPos maxPos, int offsetX, int offsetY, int offsetZ) {
        this.id = id;
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.isVariantB = false;
        this.contraptionEntityId = null;
    }

    public CompoundTag save(CompoundTag nbt) {
        nbt.putUUID("Id", this.id);
        nbt.put("MinPos", NbtUtils.writeBlockPos(this.minPos));
        nbt.put("MaxPos", NbtUtils.writeBlockPos(this.maxPos));
        nbt.putInt("OffsetX", this.offsetX);
        nbt.putInt("OffsetY", this.offsetY);
        nbt.putInt("OffsetZ", this.offsetZ);
        nbt.putBoolean("IsVariantB", this.isVariantB);
        if (this.contraptionEntityId != null) {
            nbt.putUUID("ContraptionEntityId", this.contraptionEntityId);
        }
        return nbt;
    }

    public static LabyrinthShiftZone load(CompoundTag nbt) {
        UUID id = nbt.contains("Id") ? nbt.getUUID("Id") : UUID.randomUUID();
        BlockPos min = NbtUtils.readBlockPos(nbt.getCompound("MinPos"));
        BlockPos max = NbtUtils.readBlockPos(nbt.getCompound("MaxPos"));
        int offX = nbt.getInt("OffsetX");
        int offY = nbt.getInt("OffsetY");
        int offZ = nbt.getInt("OffsetZ");

        LabyrinthShiftZone zone = new LabyrinthShiftZone(id, min, max, offX, offY, offZ);
        zone.isVariantB = nbt.getBoolean("IsVariantB");
        if (nbt.contains("ContraptionEntityId")) {
            zone.contraptionEntityId = nbt.getUUID("ContraptionEntityId");
        }
        return zone;
    }

    public void toggleState() {
        this.isVariantB = !this.isVariantB;
    }
}