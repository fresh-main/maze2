package com.mazemap.event;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.scan.MapScanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;

        // Сканер сам проверит наличие карты и обновит NBT предмета
        MapScanner.scan(sp);
    }

    // onPlayerLogout больше не нужен, так как мы не пишем в отдельные файлы на диске.
}