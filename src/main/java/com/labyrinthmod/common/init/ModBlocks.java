package com.labyrinthmod.common.init;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.block.GriverSpawnerBlock;
import com.labyrinthmod.common.block.entity.GriverSpawnerBlockEntity;
import com.labyrinthmod.common.item.BoundsStickItem;
import com.labyrinthmod.common.item.PatrolStickItem;
import com.labyrinthmod.common.item.ImposterTabletItem;
import com.labyrinthmod.common.item.ZoneStickItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, LabyrinthMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    // Блоки
    public static final RegistryObject<Block> GRIVER_SPAWNER = BLOCKS.register("griver_spawner",
            () -> new GriverSpawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
            ));


    public static final RegistryObject<Item> GRIVER_SPAWNER_ITEM = ITEMS.register("griver_spawner",
            () -> new BlockItem(GRIVER_SPAWNER.get(), new Item.Properties()));

    // Инструменты
    public static final RegistryObject<Item> PATROL_STICK = ITEMS.register("patrol_stick",
            PatrolStickItem::new);

    public static final RegistryObject<Item> ZONE_STICK = ITEMS.register("zone_stick",
            ZoneStickItem::new);

    public static final RegistryObject<Item> BOUNDS_STICK = ITEMS.register("bounds_stick",
            BoundsStickItem::new);

    public static final RegistryObject<Item> IMPOSTER_TABLET = ITEMS.register("traitor_tablet",
            ImposterTabletItem::new);
    // В поле класса ModBlocks:
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, LabyrinthMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<GriverSpawnerBlockEntity>> GRIVER_SPAWNER_BE =
            BLOCK_ENTITIES.register("griver_spawner", () ->
                    BlockEntityType.Builder.of(GriverSpawnerBlockEntity::new, GRIVER_SPAWNER.get()).build(null));
}