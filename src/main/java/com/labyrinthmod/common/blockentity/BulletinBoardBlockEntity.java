package com.labyrinthmod.common.blockentity;

import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.item.TaskItem;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BulletinBoardBlockEntity extends BlockEntity implements Container, net.minecraft.world.MenuProvider {
    private static final int MAX_TASKS = 9;
    private final List<ItemStack> tasks = new ArrayList<>(MAX_TASKS);

    public BulletinBoardBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BULLETIN_BOARD_BE.get(), pos, blockState);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
        }
    }

    public boolean addTask(ItemStack taskStack, Player player) {
        if (!(taskStack.getItem() instanceof TaskItem)) return false;
        for (int i = 0; i < MAX_TASKS; i++) {
            if (tasks.get(i).isEmpty()) {
                tasks.set(i, taskStack.split(1));
                setChanged();
                return true;
            }
        }
        return false;
    }

    public ItemStack takeTask(int slot, Player player) {
        if (slot < 0 || slot >= MAX_TASKS) return ItemStack.EMPTY;
        ItemStack task = tasks.get(slot);
        if (!task.isEmpty()) {
            tasks.set(slot, ItemStack.EMPTY);
            setChanged();
            return task;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getTask(int slot) {
        if (slot < 0 || slot >= MAX_TASKS) return ItemStack.EMPTY;
        return tasks.get(slot);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tasks.clear();
        ListTag tasksTag = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(i < tasksTag.size() ? ItemStack.of(tasksTag.getCompound(i)) : ItemStack.EMPTY);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tasksTag = new ListTag();
        for (ItemStack task : tasks) tasksTag.add(task.save(new CompoundTag()));
        tag.put("Tasks", tasksTag);
    }

    // ===== MenuProvider методы =====
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.labyrinthmod.bulletin_board");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BulletinBoardMenu(containerId, playerInventory, this);
    }

    // ===== Container методы =====
    @Override
    public int getContainerSize() { return MAX_TASKS; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : tasks) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int slot) { return getTask(slot); }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = getTask(slot);
        if (!stack.isEmpty()) {
            if (stack.getCount() <= count) {
                tasks.set(slot, ItemStack.EMPTY);
                setChanged();
                return stack;
            } else {
                ItemStack split = stack.split(count);
                setChanged();
                return split;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getTask(slot);
        tasks.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < MAX_TASKS) {
            tasks.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        tasks.clear();
        for (int i = 0; i < MAX_TASKS; i++) tasks.add(ItemStack.EMPTY);
    }
}