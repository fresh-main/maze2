package com.labyrinthmod.common.generation;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class WorldSeedHolder {

    private static volatile long worldSeed = 0;
    private static volatile boolean seedLoaded = false;

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        // Получаем сид как можно раньше, до активной генерации чанков.
        // Если маппинг ругается на worldGenOptions(), попробуй getWorldGenOptions().
        worldSeed = event.getServer().getWorldData().worldGenOptions().seed();
        seedLoaded = true;
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            worldSeed = serverLevel.getSeed();
            seedLoaded = true;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        worldSeed = 0;
        seedLoaded = false;
    }

    public static long getWorldSeed() {
        return worldSeed;
    }

    public static boolean isSeedLoaded() {
        return seedLoaded;
    }
}