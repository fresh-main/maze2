package com.labyrinthmod.common.item;

import com.labyrinthmod.common.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TaskItem extends Item {
    public TaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains("TaskTitle", 8)) {
                tooltip.add(Component.literal("Название: ").append(Component.literal(tag.getString("TaskTitle"))));
            }
            if (tag.contains("TaskDescription", 8)) {
                tooltip.add(Component.literal("Описание: ").append(Component.literal(tag.getString("TaskDescription"))));
            }
            if (tag.contains("TaskReward", 8)) {
                tooltip.add(Component.literal("Награда: ").append(Component.literal(tag.getString("TaskReward"))));
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    public static ItemStack createTask(String title, String description, String reward) {
        ItemStack stack = new ItemStack(ModItems.TASK.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("TaskTitle", title);
        tag.putString("TaskDescription", description);
        tag.putString("TaskReward", reward);
        tag.putString("Author", "System");
        stack.setTag(tag);
        return stack;
    }

    public String getTitle(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("TaskTitle", 8) ? tag.getString("TaskTitle") : "Без названия";
    }

    public String getDescription(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("TaskDescription", 8) ? tag.getString("TaskDescription") : "";
    }

    public String getReward(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("TaskReward", 8) ? tag.getString("TaskReward") : "";
    }

    public String getAuthor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains("Author", 8) ? tag.getString("Author") : "Неизвестно";
    }
}