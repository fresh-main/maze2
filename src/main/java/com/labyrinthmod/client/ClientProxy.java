package com.labyrinthmod.client;

import com.labyrinthmod.client.renderer.GriverRenderer;
import com.labyrinthmod.client.screen.AdminScreen;
import com.labyrinthmod.common.Proxy;
import com.labyrinthmod.common.entity.GriverEntityType;
import com.labyrinthmod.common.network.packet.OpenAdminMenuPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ClientProxy extends Proxy {

    @Override
    public void openAdminScreen(Object packet) {
        if (packet instanceof OpenAdminMenuPacket p) {
            Minecraft.getInstance().setScreen(new AdminScreen(p));
        }
    }

    @Override
    public void registerClientListeners() {
        // Регистрируем обработчики FORGE событий
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new RiderAttackHandler());

        // Явно регистрируем рендерер
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterRenderers);
    }

    // Ручная регистрация рендерера
    @SubscribeEvent
    public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GriverEntityType.GRIVER.get(), GriverRenderer::new);
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public boolean isDedicatedServer() {
        return false;
    }

    // Отключаем рендер руки когда игрок на гривере
    @SubscribeEvent
    public void onRenderHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.getVehicle() instanceof com.labyrinthmod.common.entity.GriverEntity) {
            event.setCanceled(true);
        }
    }

    public net.minecraft.client.player.LocalPlayer getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}