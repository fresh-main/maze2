package com.infection.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Provider зацеплен к каждому Player через {@link InfectionAttacher}.
 *
 * ВАЖНО: LazyOptional при вызове {@link #invalidate()} становится навсегда «empty»
 * (это финальное состояние LazyOptional). Это создавало баг при PlayerEvent.Clone:
 * после {@code invalidateCaps()} старый игрок навсегда терял capability и
 * {@code copyFrom} не срабатывал. Поэтому в {@link #getCapability} мы пересоздаём
 * lazy, если предыдущий был invalidated — это безопасно, т.к. данные хранятся в {@code data}.
 */
public class InfectionProvider implements ICapabilitySerializable<CompoundTag> {

    public static final Capability<IInfection> CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private final InfectionData data = new InfectionData();
    private LazyOptional<IInfection> lazy = LazyOptional.of(() -> data);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == CAP) {
            if (!lazy.isPresent()) {
                lazy = LazyOptional.of(() -> data);
            }
            return lazy.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.save();
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        data.load(tag);
    }

    public void invalidate() {
        lazy.invalidate();
    }

    public static Optional<IInfection> get(Player player) {
        if (player == null) return Optional.empty();
        return player.getCapability(CAP).resolve();
    }
}
