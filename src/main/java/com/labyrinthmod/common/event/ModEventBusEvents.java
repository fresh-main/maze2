package com.labyrinthmod.common.event;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import com.labyrinthmod.common.generation.LabyrinthBiomeSource;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;

@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventBusEvents {

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        // Регистрация биом-сорса
        event.register(Registries.BIOME_SOURCE, helper -> {
            helper.register(
                    new ResourceLocation("labyrinthmod", "labyrinth_biome_source"),
                    LabyrinthBiomeSource.CODEC
            );
            System.out.println("[LabyrinthMod] Registered LabyrinthBiomeSource!");
        });

        // Регистрация чанк-генератора (если ещё не зарегистрирован)
        event.register(Registries.CHUNK_GENERATOR, helper -> {
            helper.register(
                    new ResourceLocation("labyrinthmod", "labyrinth_chunk_generator"),
                    LabyrinthChunkGenerator.CODEC
            );
            System.out.println("[LabyrinthMod] Registered LabyrinthChunkGenerator!");
        });
    }
}