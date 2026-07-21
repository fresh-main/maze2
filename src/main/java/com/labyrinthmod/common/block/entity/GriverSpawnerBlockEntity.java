package com.labyrinthmod.common.block.entity;

import com.labyrinthmod.common.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class GriverSpawnerBlockEntity extends BlockEntity {
    private UUID griverUUID = null;

    public GriverSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GRIVER_SPAWNER_BE.get(), pos, state);
    }

    public UUID getGriverUUID() {
        return griverUUID;
    }

    public void setGriverUUID(UUID uuid) {
        this.griverUUID = uuid;
        setChanged(); // Помечаем блок как изменённый для сохранения
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasGriver() {
        return griverUUID != null;
    }

    public void clearGriver() {
        this.griverUUID = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (griverUUID != null) {
            tag.putUUID("GriverUUID", griverUUID);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("GriverUUID")) {
            griverUUID = tag.getUUID("GriverUUID");
        } else {
            griverUUID = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}