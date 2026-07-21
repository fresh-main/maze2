package com.labyrinthmod.common.blockentity;

import com.labyrinthmod.common.init.ModBlockEntities;
import com.labyrinthmod.common.item.TaskItem;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BulletinBoardBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
    private static final int MAX_TASKS = 9;
    private final List<ItemStack> tasks = new ArrayList<>(MAX_TASKS);
    private Component customName;

    public BulletinBoardBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BULLETIN_BOARD.get(), pos, blockState);
        for (int i = 0; i < MAX_TASKS; i++) {
            tasks.add(ItemStack.EMPTY);
        }
    }

    public void tick() {
        // Тик логика если нужна
    }

    public boolean addTask(ItemStack taskStack, Player player) {
        if (!(taskStack.getItem() instanceof TaskItem)) {
            return false;
        }

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
        if (slot < 0 || slot >= MAX_TASKS) {
            return ItemStack.EMPTY;
        }

        ItemStack task = tasks.get(slot);
        if (!task.isEmpty()) {
            tasks.set(slot, ItemStack.EMPTY);
            setChanged();
            return task;
        }
        return ItemStack.EMPTY;
    }

    public ItemStack getTask(int slot) {
        if (slot < 0 || slot >= MAX_TASKS) {
            return ItemStack.EMPTY;
        }
        return tasks.get(slot);
    }

    public List<ItemStack> getAllTasks() {
        return new ArrayList<>(tasks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tasks.clear();
        ListTag tasksTag = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < MAX_TASKS; i++) {
            if (i < tasksTag.size()) {
                tasks.add(ItemStack.of(tasksTag.getCompound(i)));
            } else {
                tasks.add(ItemStack.EMPTY);
            }
        }
        if (tag.contains("CustomName", Tag.TAG_STRING)) {
            this.customName = Component.Serializer.fromJson(tag.getString("CustomName"));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ListTag tasksTag = new ListTag();
        for (ItemStack task : tasks) {
            tasksTag.add(task.save(new CompoundTag()));
        }
        tag.put("Tasks", tasksTag);
        if (this.customName != null) {
            tag.putString("CustomName", Component.Serializer.toJson(this.customName));
        }
    }


    public Component getName() {
        return this.customName != null ? this.customName : Component.translatable("container.labyrinthmod.bulletin_board");
    }

    @Nullable

    public Component getCustomName() {
        return this.customName;
    }

    public void setCustomName(Component name) {
        this.customName = name;
    }

    // ===== Реализация MenuProvider =====
    @Override
    public Component getDisplayName() {
        return getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BulletinBoardMenu(containerId, playerInventory, this);
    }
}