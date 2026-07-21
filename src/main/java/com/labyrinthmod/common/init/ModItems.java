package com.labyrinthmod.common.init;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.item.TaskItem;
import com.labyrinthmod.common.item.WritableTaskItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    public static final RegistryObject<TaskItem> TASK = ITEMS.register("task",
            () -> new TaskItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<WritableTaskItem> WRITABLE_TASK = ITEMS.register("writable_task",
            () -> new WritableTaskItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}