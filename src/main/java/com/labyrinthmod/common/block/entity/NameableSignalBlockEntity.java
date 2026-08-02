package com.labyrinthmod.common.block.entity;

import com.labyrinthmod.common.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class NameableSignalBlockEntity extends BlockEntity {
    private String customName = "Без имени";
    private boolean isPowered = false;

    public NameableSignalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAMEABLE_SIGNAL_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("CustomName", this.customName);
        tag.putBoolean("IsPowered", this.isPowered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("CustomName")) {
            this.customName = tag.getString("CustomName");
            // Регистрируем имя в менеджере при загрузке мира (чтобы после перезахода команда снова видела блок)
            NamedBlockManager.registerName(this.worldPosition, this.customName);
        }
        if (tag.contains("IsPowered")) {
            this.isPowered = tag.getBoolean("IsPowered");
        }
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        this.customName = name;
        // Обновляем имя в менеджере
        NamedBlockManager.registerName(this.worldPosition, name);
        markDirtyAndNotify();
    }

    public boolean isPowered() {
        return isPowered;
    }

    public void setPowered(boolean powered) {
        if (this.isPowered != powered) {
            this.isPowered = powered;
            markDirtyAndNotify();
        }
    }

    private void markDirtyAndNotify() {
        if (this.level != null) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
    }
}