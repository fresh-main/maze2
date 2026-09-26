package com.labyrinthmod.common.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class TimeSpeedHandler {

    private static double timeSpeed = 1.0;
    private static boolean enabled = false;
    private static final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, Double> remainders = new HashMap<>();

    public static double getTimeSpeed() {
        return timeSpeed;
    }

    public static void setTimeSpeed(double speed) {
        timeSpeed = Math.max(0.05, Math.min(1000.0, speed));
        remainders.clear();
        System.out.println("[TimeSpeed] Установлена скорость: " + timeSpeed);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        TimeSpeedHandler.enabled = enabled;
        if (!enabled) remainders.clear();
        System.out.println("[TimeSpeed] Enabled: " + enabled);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!enabled) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerLevel level : server.getAllLevels()) {
            // Minecraft уже прибавляет один тик времени. Накапливаем только
            // разницу до требуемого множителя, сохраняя дробную часть между тиками.
            double accumulated = remainders.getOrDefault(level.dimension(), 0.0) + (timeSpeed - 1.0);
            long correction = accumulated >= 0.0 ? (long) Math.floor(accumulated) : (long) Math.ceil(accumulated);
            remainders.put(level.dimension(), accumulated - correction);
            if (correction != 0L) level.setDayTime(level.getDayTime() + correction);
        }
    }
}
