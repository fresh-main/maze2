package com.labyrinthmod.common.event;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.labyrinthmod.common.event.DoorScheduleHandler.worldTickCount;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class ContraptionScheduleHandler {

    // День, в который команда уже была выполнена.
    // -1 означает "ещё ни разу".
    private static long lastRunDay = -1;

    // Окно вокруг полуночи (18000). Берём с запасом,
    // потому что тик может "перескочить" ровно через 18000.
    private static final long MIDNIGHT_START = 17990;
    private static final long MIDNIGHT_END   = 18010;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        worldTickCount++;

        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        long dayTime = overworld.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        boolean isMidnight = timeOfDay >= MIDNIGHT_START && timeOfDay < MIDNIGHT_END;

        if (isMidnight && lastRunDay != day) {
            lastRunDay = day;

            CommandSourceStack source = server.createCommandSourceStack();
            int result = server.getCommands().performPrefixedCommand(source, "labyrinth shift");
            // result > 0 — команда найдена и выполнена; 0 — команда не найдена / не выполнена
        }
    }
}