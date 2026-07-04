package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public class PatrolTimeHandler {

    private static long lastProcessedDay = -1;
    private static final int CHECK_INTERVAL_TICKS = 20; // раз в секунду

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;

        PatrolManager m = PatrolManager.get(level);
        if (m == null) return;
        if (!m.isTimeBasedEmergenceEnabled()) return;
        if (m.isGlobalPatrolActive()) return;
        if (m.getPatrolPoints().isEmpty()) return;

        long dayTime = level.getDayTime() % 24000L;
        long target = m.getEmergenceTime();
        long currentDay = level.getDayTime() / 24000L;

        // Окно допуска — ±CHECK_INTERVAL_TICKS
        long diff = Math.abs(dayTime - target);
        if (diff > 12000) diff = 24000 - diff;

        if (diff <= CHECK_INTERVAL_TICKS && currentDay != lastProcessedDay) {
            lastProcessedDay = currentDay;
            startGlobalPatrol(level, m);
        }
    }

    private static void startGlobalPatrol(ServerLevel level, PatrolManager m) {
        m.setGlobalPatrolActive(true);
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        int count = 0;
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                g.joinGlobalPatrol();
                count++;
            }
        }
        LabyrinthMod.LOGGER.info("[Patrol] Time-based emergence triggered at {}. Started {} grivers.",
                level.getDayTime() % 24000, count);
    }
}
