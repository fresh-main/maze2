package com.labyrinthmod.common.item;

import com.labyrinthmod.LabyrinthMod;
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

    // 1. Создаем свой собственный регистр предметов
    public static final DeferredRegister<Item> TASK_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    // 2. Регистрируем сам предмет с именем "task_item"
    public static final RegistryObject<Item> TASK_ITEM = TASK_ITEMS.register("task_item",
            () -> new TaskItem(new Item.Properties().stacksTo(1))
    );

    // 3. Метод, который мы вызовем из главного класса для активации регистрации
    public static void register(IEventBus modEventBus) {
        TASK_ITEMS.register(modEventBus);
    }

    public TaskItem(Properties properties) {
        super(properties);
    }

    // Подсказка при наведении на предмет (для версии 1.20.1 используется Level, а не TooltipContext)
    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        if (stack.hasTag()) {
            var tag = stack.getTag();
            tooltipComponents.add(Component.literal("§6" + tag.getString("Title")));
            tooltipComponents.add(Component.literal("§7" + tag.getString("Description")));
            tooltipComponents.add(Component.literal("§aНаграда: " + tag.getString("Reward")));
            tooltipComponents.add(Component.literal("§8Автор: " + tag.getString("Author")));
        }
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}