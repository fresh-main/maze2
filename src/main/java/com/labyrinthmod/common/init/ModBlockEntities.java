package com.labyrinthmod.common.init;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "labyrinthmod");

    public static final RegistryObject<BlockEntityType<BulletinBoardBlockEntity>> BULLETIN_BOARD =
            BLOCK_ENTITIES.register("bulletin_board", () ->
                    BlockEntityType.Builder.of(BulletinBoardBlockEntity::new, ModBlocks.BULLETIN_BOARD.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}