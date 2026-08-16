package com.labyrinthmod.common.event;

import net.minecraft.world.entity.monster.Slime;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class SlimeSpawnHandler {

    @SubscribeEvent
    public static void onSlimeFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getEntity() instanceof Slime) {
            event.setCanceled(true);
        }
    }
}