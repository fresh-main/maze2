package com.labyrinthmod.common.block.entity;

import com.labyrinthmod.common.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class NameableSignalBlockEntity extends BlockEntity {

    // ★ СТАТИЧЕСКИЙ КЭШ для мгновенного поиска по имени ★
    private static final Map<String, List<BlockPos>> NAME_CACHE = new HashMap<>();

    private String customName = "Без имени";
    private boolean isPowered = false;
    private int ticksRemaining = 0;

    public NameableSignalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NAMEABLE_SIGNAL_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("CustomName", customName);
        tag.putBoolean("IsPowered", isPowered);
        tag.putInt("TicksRemaining", ticksRemaining);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("CustomName")) {
            this.customName = tag.getString("CustomName");
        }
        if (tag.contains("IsPowered")) {
            this.isPowered = tag.getBoolean("IsPowered");
        }
        if (tag.contains("TicksRemaining")) {
            this.ticksRemaining = tag.getInt("TicksRemaining");
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        addToCache();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        removeFromCache();
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        if (!this.customName.equals(name)) {
            removeFromCache();
            this.customName = name;
            addToCache();
            markDirtyAndNotify();
        }
    }

    public boolean isPowered() {
        return isPowered;
    }

    public void setPowered(boolean powered) {
        if (this.isPowered != powered) {
            this.isPowered = powered;
            if (powered) {
                this.ticksRemaining = 5; // 5 тиков до отключения
            } else {
                this.ticksRemaining = 0;
            }
            markDirtyAndNotify();
        }
    }

    public void tick() {
        if (isPowered && ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining == 0) {
                setPowered(false);
            }
        }
    }

    private void markDirtyAndNotify() {
        if (this.level != null) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
    }

    private void addToCache() {
        if (customName != null && !customName.isEmpty() && !customName.equals("Без имени")) {
            NAME_CACHE.computeIfAbsent(customName, k -> new ArrayList<>()).add(this.worldPosition);
        }
    }

    private void removeFromCache() {
        if (customName != null && NAME_CACHE.containsKey(customName)) {
            List<BlockPos> list = NAME_CACHE.get(customName);
            list.remove(this.worldPosition);
            if (list.isEmpty()) {
                NAME_CACHE.remove(customName);
            }
        }
    }

    // ★ ЭТОТ МЕТОД БЫЛ ОТСУТСТВЕН, ДОБАВЛЯЕМ ЕГО ★
    public static List<BlockPos> getPositionsByName(String name) {
        return NAME_CACHE.getOrDefault(name, Collections.emptyList());
    }
}