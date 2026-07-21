package com.labyrinthmod.common.menu;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class BulletinBoardMenu extends AbstractContainerMenu {
    private final BulletinBoardBlockEntity blockEntity;
    private final Player player;

    public BulletinBoardMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, (BulletinBoardBlockEntity) playerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public BulletinBoardMenu(int containerId, Inventory playerInventory, BulletinBoardBlockEntity blockEntity) {
        super(ModMenuTypes.BULLETIN_BOARD_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;

        // Слоты для заданий (3x3 сетка)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                int x = 44 + col * 60;
                int y = 36 + row * 60;
                this.addSlot(new TaskSlot(blockEntity, slotIndex, x, y));
            }
        }

        // Инвентарь игрока
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 200 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 258));
        }
    }

    // ВАЖНО: убрали 'static', чтобы класс имел доступ к полю 'player' внешнего класса
    private class TaskSlot extends Slot {
        private final BulletinBoardBlockEntity blockEntity;
        private final int slotIndex;

        public TaskSlot(BulletinBoardBlockEntity blockEntity, int slotIndex, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.blockEntity = blockEntity;
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false; // Нельзя класть предметы в слоты доски через GUI
        }

        @Override
        public boolean mayPickup(Player player) {
            return !this.getItem().isEmpty();
        }

        @Override
        public ItemStack getItem() {
            return blockEntity.getTask(slotIndex);
        }

        @Override
        public void set(ItemStack stack) {
            // Ничего не делаем, чтобы нельзя было положить предмет через shift-click
        }

        @Override
        public ItemStack remove(int amount) {
            if (!blockEntity.getTask(slotIndex).isEmpty()) {
                // Теперь 'player' берется из внешнего класса BulletinBoardMenu
                return blockEntity.takeTask(slotIndex, player);
            }
            return ItemStack.EMPTY;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity != null &&
                player.distanceToSqr(this.blockEntity.getBlockPos().getX() + 0.5,
                        this.blockEntity.getBlockPos().getY() + 0.5,
                        this.blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }

    public BulletinBoardBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public Player getPlayer() {
        return player;
    }
}