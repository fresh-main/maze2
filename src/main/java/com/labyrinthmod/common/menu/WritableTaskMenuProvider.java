package com.labyrinthmod.common.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class WritableTaskMenuProvider implements net.minecraft.world.MenuProvider {
    private final ItemStack writableTask;

    public WritableTaskMenuProvider(ItemStack writableTask) {
        this.writableTask = writableTask;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.labyrinthmod.writable_task");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WritableTaskMenu(containerId, playerInventory, writableTask);
    }

    public ItemStack getWritableTask() {
        return writableTask;
    }
}