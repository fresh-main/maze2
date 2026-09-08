package com.labyrinthmod.common.item;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class TaskItem extends Item {

    public static final DeferredRegister<Item> TASK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    public static final RegistryObject<Item> TASK_ITEM = TASK_ITEMS.register("task_item",
            () -> new TaskItem(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus modEventBus) {
        TASK_ITEMS.register(modEventBus);
    }

    public TaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        if (stack.hasTag()) {
            var tag = stack.getTag();
            if (tag != null) {
                String title = tag.getString("Title");
                String desc = tag.getString("Description");
                String author = tag.getString("Author");

                // ИСПРАВЛЕНО: Убраны символы §, которые не работают в Component.literal.
                // Теперь используются стили ChatFormatting.
                if (!title.isEmpty()) {
                    tooltipComponents.add(Component.literal(title).withStyle(ChatFormatting.GOLD));
                }
                if (!desc.isEmpty()) {
                    tooltipComponents.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
                }
                if (!author.isEmpty()) {
                    tooltipComponents.add(Component.literal("Автор: " + author).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}