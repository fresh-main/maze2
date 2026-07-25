package com.labyrinthmod.common.item;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.client.screen.TaskViewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class TaskScrollItem extends Item {

    public static final DeferredRegister<Item> TASK_SCROLL_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    public static final RegistryObject<Item> TASK_SCROLL = TASK_SCROLL_ITEMS.register("task_scroll",
            () -> new TaskScrollItem(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus modEventBus) {
        TASK_SCROLL_ITEMS.register(modEventBus);
    }

    public TaskScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }

        // Открываем экран задания на клиенте
        if (stack.hasTag()) {
            Minecraft.getInstance().setScreen(new TaskViewScreen(stack));
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        if (stack.hasTag()) {
            var tag = stack.getTag();
            tooltipComponents.add(Component.literal("§6" + tag.getString("Title")));
            tooltipComponents.add(Component.literal("§7ПКМ чтобы прочитать"));
        } else {
            tooltipComponents.add(Component.literal("§7Пустой свиток"));
        }
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}