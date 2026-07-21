package com.labyrinthmod.common.item;

import com.labyrinthmod.common.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import com.labyrinthmod.common.menu.WritableTaskMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

public class WritableTaskItem extends Item {
    public WritableTaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // Открываем GUI для написания задания
            NetworkHooks.openScreen(serverPlayer, new WritableTaskMenuProvider(stack));
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.success(stack);
    }

    public static ItemStack createWritableTask() {
        ItemStack stack = new ItemStack(ModItems.WRITABLE_TASK.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("TaskTitle", "");
        tag.putString("TaskDescription", "");
        tag.putString("TaskReward", "");
        stack.setTag(tag);
        return stack;
    }

    public static ItemStack convertToTask(ItemStack writableStack, String title, String description, String reward, String author) {
        ItemStack taskStack = new ItemStack(ModItems.TASK.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("TaskTitle", title);
        tag.putString("TaskDescription", description);
        tag.putString("TaskReward", reward);
        tag.putString("Author", author);
        taskStack.setTag(tag);
        return taskStack;
    }
}