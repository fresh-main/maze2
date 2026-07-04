package com.labyrinthmod.common.event;

import com.labyrinthmod.common.zone.WindZoneManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class WindZoneTickHandler {

    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        tickCounter++;
        // Проверяем каждые 4 тика (5 раз в секунду) - достаточно для эффектов пыли
        if (tickCounter % 4 != 0) return;
        if (tickCounter > 100) tickCounter = 0; // Сброс для предотвращения переполнения

        WindZoneManager manager = WindZoneManager.get(level);
        if (manager != null && manager.isEnabled()) {
            manager.tick(level);
        }
    }
}