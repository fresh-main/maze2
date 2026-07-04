package com.otbor.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class LockedSlot extends Slot {

    private final Player owner;

    public LockedSlot(Container container, int slot, int x, int y, Player owner) {
        super(container, slot, x, y);
        this.owner = owner;
    }

    @Override
    public boolean isActive() {
        return isCreative();
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return isCreative();
    }

    @Override
    public boolean mayPickup(Player player) {
        return player.isCreative();
    }

    private boolean isCreative() {
        return owner != null && owner.isCreative();
    }
}
