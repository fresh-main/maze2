package com.labyrinthmod.common.event;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BulletinBoardTickHandler {

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.level.isClientSide || event.phase != TickEvent.Phase.END) {
            return;
        }

    }
}