package com.labyrinthmod.common.menu;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BulletinBoardMenu extends AbstractContainerMenu {
    private final BulletinBoardBlockEntity blockEntity;
    private final Player player;

    public BulletinBoardMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, buf));
    }

    private static BulletinBoardBlockEntity getBlockEntity(Inventory playerInventory, FriendlyByteBuf buf) {
        if (buf == null) return null;
        BlockEntity be = playerInventory.player.level().getBlockEntity(buf.readBlockPos());
        return be instanceof BulletinBoardBlockEntity board ? board : null;
    }

    public BulletinBoardMenu(int containerId, Inventory playerInventory, BulletinBoardBlockEntity blockEntity) {
        super(ModMenuTypes.BULLETIN_BOARD_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;

        // НЕ добавляем слоты - они не нужны, всё рисуем вручную
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity != null && player.distanceToSqr(
                blockEntity.getBlockPos().getX() + 0.5,
                blockEntity.getBlockPos().getY() + 0.5,
                blockEntity.getBlockPos().getZ() + 0.5
        ) <= 64.0;
    }

    public BulletinBoardBlockEntity getBlockEntity() {
        return blockEntity;
    }
}