package com.labyrinthmod.common.block;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.block.entity.MazeMonitorBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// Самостоятельный реестр, никуда больше не встроен — добавить в конструктор
// LabyrinthMod вызов: MazeMonitorRegistry.register(modEventBus);
public final class MazeMonitorRegistry {

    private MazeMonitorRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LabyrinthMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LabyrinthMod.MOD_ID);

    public static final RegistryObject<Block> MAZE_MONITOR =
            BLOCKS.register("maze_monitor", MazeMonitorBlock::new);

    public static final RegistryObject<Item> MAZE_MONITOR_ITEM =
            ITEMS.register("maze_monitor", () -> new BlockItem(MAZE_MONITOR.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<MazeMonitorBlockEntity>> MAZE_MONITOR_BE =
            BLOCK_ENTITIES.register("maze_monitor",
                    () -> BlockEntityType.Builder.of(MazeMonitorBlockEntity::new, MAZE_MONITOR.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
