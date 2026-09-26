package com.labyrinthmod.client;

import com.labyrinthmod.common.event.TimeSpeedHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Плавно двигает клиентское небо между серверными пакетами синхронизации времени. */
@Mod.EventBusSubscriber(modid = "labyrinthmod", value = Dist.CLIENT)
public final class SmoothTimeClientHandler {
    private static double remainder;

    private SmoothTimeClientHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) return;
        if (!TimeSpeedHandler.isEnabled()) {
            remainder = 0.0;
            return;
        }
        remainder += TimeSpeedHandler.getTimeSpeed() - 1.0;
        long correction = remainder >= 0.0 ? (long) Math.floor(remainder) : (long) Math.ceil(remainder);
        remainder -= correction;
        if (correction != 0L) minecraft.level.setDayTime(minecraft.level.getDayTime() + correction);
    }
}
