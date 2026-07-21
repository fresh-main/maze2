package com.labyrinthmod.common.menu;

import com.labyrinthmod.common.init.ModMenuTypes;
import com.labyrinthmod.common.item.WritableTaskItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WritableTaskMenu extends AbstractContainerMenu {
    private final ItemStack writableTask;
    private final Player player;

    public WritableTaskMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readItem());
    }

    public WritableTaskMenu(int containerId, Inventory playerInventory, ItemStack writableTask) {
        super(ModMenuTypes.WRITABLE_TASK_MENU.get(), containerId);
        this.writableTask = writableTask;
        this.player = playerInventory.player;

        // Добавляем слоты инвентаря
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 180 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 238));
        }
    }

    public ItemStack getWritableTask() {
        return writableTask;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    public void saveAndConvert(String title, String description, String reward) {
        if (!writableTask.isEmpty()) {
            ItemStack taskStack = WritableTaskItem.convertToTask(writableTask, title, description, reward, player.getName().getString());
            player.getInventory().add(taskStack);
        }
    }
}