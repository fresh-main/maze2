package com.labyrinthmod.client;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT)
public class RiderRenderHandler {

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof GriverEntity) {
            // ОТКЛЮЧАЕМ ОТОБРАЖЕНИЕ РУК И ПРЕДМЕТОВ
            event.setCanceled(true);
        }
    }

    /**
     * Полностью скрывает модель игрока (тело + броня + предметы + nameplate),
     * пока он сидит на гривере. MobEffects.INVISIBILITY (как было раньше) не
     * прячет броню — приходилось видеть «летающий» шлем над гривером. Здесь
     * мы отменяем весь RenderPlayerEvent.Pre — для всех клиентов, не только
     * самого наездника.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof GriverEntity) {
            event.setCanceled(true);
        }
    }
}