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

        System.out.println("[BB TickHandler] Ticking " + BulletinBoardBlockEntity.ALL_BOARDS.size() + " boards");

        for (BulletinBoardBlockEntity board : BulletinBoardBlockEntity.ALL_BOARDS) {
            System.out.println("[BB TickHandler] Ticking board at " + board.getBlockPos() + ", interval=" + board.getSpawnIntervalSeconds());
            board.tick();
        }
    }
}