package com.labyrinthmod.common.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class TimeSpeedHandler {

    private static double timeSpeed = 1.0;
    private static boolean enabled = false;
    private static int tickCounter = 0;

    public static double getTimeSpeed() {
        return timeSpeed;
    }

    public static void setTimeSpeed(double speed) {
        timeSpeed = Math.max(0.1, Math.min(10.0, speed));
        System.out.println("[TimeSpeed] Установлена скорость: " + timeSpeed);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        TimeSpeedHandler.enabled = enabled;
        System.out.println("[TimeSpeed] Enabled: " + enabled);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!enabled) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        tickCounter++;

        // Логируем каждые 100 тиков (5 секунд)
        if (tickCounter % 100 == 0) {
            System.out.println("[TimeSpeed] Тик #" + tickCounter + ", speed=" + timeSpeed + ", enabled=" + enabled);
        }

        for (ServerLevel level : server.getAllLevels()) {
            long currentTime = level.getDayTime();
            long ticksToAdd = (long) Math.ceil(timeSpeed);

            if (ticksToAdd > 1) {
                level.setDayTime(currentTime + ticksToAdd);

                // Логируем изменение времени каждые 100 тиков
                if (tickCounter % 100 == 0) {
                    System.out.println("[TimeSpeed] Время изменено: " + currentTime + " -> " + (currentTime + ticksToAdd));
                }
            }
        }
    }
}