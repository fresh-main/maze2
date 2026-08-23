package com.labyrinthmod.common.block.entity;

import com.labyrinthmod.common.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NameableSignalBlockEntity extends BlockEntity {

    // ★ КЭШ с привязкой к измерению (миру) ★
    private static final Map<ResourceKey<Level>, Map<String, List<BlockPos>>> NAME_CACHE = new ConcurrentHashMap<>();

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
        addToCache();
    }

    @Override
    public void setRemoved() {
        removeFromCache();
        super.setRemoved();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        addToCache();
    }

    private void markDirtyAndNotify() {
        if (this.level != null) {
            this.setChanged();
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
            this.level.updateNeighborsAt(this.worldPosition, this.getBlockState().getBlock());
        }
    }

    // --- Работа с кэшем (привязанным к уровню) ---

    private void addToCache() {
        if (this.level == null) return;
        if (customName != null && !customName.isEmpty() && !customName.equals("Без имени")) {
            ResourceKey<Level> dimension = this.level.dimension();
            Map<String, List<BlockPos>> dimensionMap = NAME_CACHE.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
            dimensionMap.computeIfAbsent(customName, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(this.worldPosition);
        }
    }

    private void removeFromCache() {
        if (this.level == null) return;
        if (customName != null && !customName.isEmpty()) {
            ResourceKey<Level> dimension = this.level.dimension();
            Map<String, List<BlockPos>> dimensionMap = NAME_CACHE.get(dimension);
            if (dimensionMap != null) {
                List<BlockPos> list = dimensionMap.get(customName);
                if (list != null) {
                    list.remove(this.worldPosition);
                    if (list.isEmpty()) {
                        dimensionMap.remove(customName);
                    }
                }
                if (dimensionMap.isEmpty()) {
                    NAME_CACHE.remove(dimension);
                }
            }
        }
    }

    // ★ Публичный метод для получения позиций по имени в конкретном мире ★
    public static List<BlockPos> getPositionsByName(String name, Level level) {
        if (level == null) return Collections.emptyList();
        ResourceKey<Level> dimension = level.dimension();
        Map<String, List<BlockPos>> dimensionMap = NAME_CACHE.get(dimension);
        if (dimensionMap == null) return Collections.emptyList();
        List<BlockPos> list = dimensionMap.get(name);
        return list != null ? list : Collections.emptyList();
    }

    // ★ Метод для очистки кэша при выгрузке мира (вызывать из события) ★
    public static void clearCacheForLevel(Level level) {
        if (level != null) {
            NAME_CACHE.remove(level.dimension());
        }
    }

    // --- Геттеры/сеттеры ---

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String name) {
        removeFromCache();
        this.customName = name;
        addToCache();
        markDirtyAndNotify();
    }

    public boolean isPowered() {
        return isPowered;
    }

    public void setPowered(boolean powered) {
        this.isPowered = powered;
        markDirtyAndNotify();
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public void setTicksRemaining(int ticks) {
        this.ticksRemaining = ticks;
        markDirtyAndNotify();
    }

    public void tick() {
        if (this.level == null || this.level.isClientSide()) return;

        if (isPowered && ticksRemaining > 0) {
            ticksRemaining--;
            if (ticksRemaining <= 0) {
                isPowered = false;
                markDirtyAndNotify();
            }
            setChanged();
        }
    }
}