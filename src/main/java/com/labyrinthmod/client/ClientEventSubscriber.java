package com.labyrinthmod.client;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.client.renderer.GriverRenderer;
import com.labyrinthmod.common.entity.GriverEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventSubscriber {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GriverEntityType.GRIVER.get(), GriverRenderer::new);
    }
}