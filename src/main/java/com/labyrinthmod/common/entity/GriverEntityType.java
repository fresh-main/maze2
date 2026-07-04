package com.labyrinthmod.common.entity;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class GriverEntityType {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LabyrinthMod.MOD_ID);

    public static final RegistryObject<EntityType<GriverEntity>> GRIVER =
            ENTITY_TYPES.register("griver",
                    () -> EntityType.Builder.<GriverEntity>of(GriverEntity::new, MobCategory.CREATURE)
                            .sized(1.0F, 3.0F)
                            .setTrackingRange(64)
                            .setUpdateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "griver").toString())
            );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}