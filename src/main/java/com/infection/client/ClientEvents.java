package com.infection.client;

import com.infection.client.minievent.EventHotkeysOverlay;
import com.infection.client.minievent.JumpscareOverlay;
import com.infection.client.overlay.HallucinationOverlay;
import com.infection.client.render.InfectedSensesLayer;
import com.infection.client.render.InfectionRenderLayer;
import com.infection.client.render.InfectionTextureCache;
import com.infection.client.render.MiniEventBlackLayer;
import com.infection.client.sound.InfectionAmbienceSound;
import com.infection.network.Network;
import com.infection.network.packet.C2SMiniEventActionPacket;
import com.infection.network.packet.C2SRequestInfectionListPacket;
import com.infection.settings.ClientSettings;
import com.labyrinthmod.LabyrinthMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    /**
     * При выходе с сервера / выгрузке клиентского мира — чистим все stale-снапшоты
     * мини-ивентов и галлюцинаций. Иначе при дисконнекте посреди ACTIVE jumpscare
     * статический BY_ADMIN остаётся с прошлым endMs, и при заходе на ЛЮБОЙ другой
     * сервер JumpscareOverlay снова рисует строб (пока endMs не пройдёт).
     */
    @SubscribeEvent
    public static void onClientDisconnect(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        com.infection.client.minievent.ClientMiniEventState.clear();
        com.infection.client.overlay.HallucinationOverlay.INSTANCE.resetState();
        com.infection.client.ClientInfectionCache.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload e) {
        if (!(e.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel)) return;
        com.infection.client.minievent.ClientMiniEventState.clear();
        com.infection.client.overlay.HallucinationOverlay.INSTANCE.resetState();
    }

    // ===== MOD bus =====

    @Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        @SubscribeEvent
        public static void onRegisterKeyBindings(RegisterKeyMappingsEvent e) {
            e.register(KeyBindings.OPEN_LIST);
            e.register(KeyBindings.EVENT_LAUNCH);
            e.register(KeyBindings.EVENT_CANCEL);
            e.register(KeyBindings.EVENT_SMOKE_HIDE);
            e.register(KeyBindings.EVENT_SMOKE_SHOW);
        }

        @SubscribeEvent
        public static void onRegisterOverlays(RegisterGuiOverlaysEvent e) {
            e.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "hallucinations", HallucinationOverlay.INSTANCE);
            // Jumpscare-вспышка — поверх всего, чтобы перекрывать любой UI.
            e.registerAboveAll("infection_jumpscare", JumpscareOverlay.INSTANCE);
            // Подсказка с хоткеями активного ивентика — только для creative-игроков.
            e.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "infection_event_hotkeys",
                    EventHotkeysOverlay.INSTANCE);
        }

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer =
                        event.getSkin(skin);
                if (renderer instanceof PlayerRenderer pr) {
                    pr.addLayer(new InfectionRenderLayer(pr));
                    pr.addLayer(new MiniEventBlackLayer(pr));
                    pr.addLayer(new InfectedSensesLayer(pr));
                }
            }
        }

        @SubscribeEvent
        public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) rm -> {
                InfectionTextureCache.clearCache();
                InfectionRenderLayer.clearExistsCache();
            });
        }
    }

    // ===== FORGE bus =====

    private static InfectionAmbienceSound ambienceSound;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        // ОТКРЫТИЕ АДМИН-СПИСКА (V)
        while (KeyBindings.OPEN_LIST.consumeClick()) {
            Network.CHANNEL.sendToServer(new C2SRequestInfectionListPacket());
        }

        // ХОТКЕЙ ЗАПУСКА ИНВЕНТИКА (G)
        while (KeyBindings.EVENT_LAUNCH.consumeClick()) {
            Network.CHANNEL.sendToServer(new C2SMiniEventActionPacket(
                    C2SMiniEventActionPacket.ACTION_LAUNCH, 0));
        }

        // ХОТКЕЙ ОТМЕНЫ ИНВЕНТИКА (B)
        while (KeyBindings.EVENT_CANCEL.consumeClick()) {
            Network.CHANNEL.sendToServer(new C2SMiniEventActionPacket(
                    C2SMiniEventActionPacket.ACTION_CANCEL, 0));
        }

        // ХОТКЕЙ SMOKE HIDE (R) — старт SMOKE-ивентика, модель админа → чёрный силуэт.
        while (KeyBindings.EVENT_SMOKE_HIDE.consumeClick()) {
            Network.CHANNEL.sendToServer(new C2SMiniEventActionPacket(
                    C2SMiniEventActionPacket.ACTION_SMOKE_HIDE, 0));
        }

        // ХОТКЕЙ SMOKE SHOW (T) — конец SMOKE-ивентика, модель → нормальный вид.
        while (KeyBindings.EVENT_SMOKE_SHOW.consumeClick()) {
            Network.CHANNEL.sendToServer(new C2SMiniEventActionPacket(
                    C2SMiniEventActionPacket.ACTION_SMOKE_SHOW, 0));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            ambienceSound = null;
            return;
        }

        int level = ClientInfectionCache.get(mc.player.getUUID());
        int threshold = ClientSettings.get().ambienceStartLevel;
        int stopThreshold = Math.max(0, threshold - 5);
        boolean isPlaying = ambienceSound != null && !ambienceSound.isStopped();
        boolean shouldStart = !isPlaying && level >= threshold;
        boolean shouldStop  =  isPlaying && level <  stopThreshold;

        if (shouldStart) {
            ambienceSound = new InfectionAmbienceSound(mc.player);
            mc.getSoundManager().play(ambienceSound);
        } else if (shouldStop) {
            mc.getSoundManager().stop(ambienceSound);
            ambienceSound = null;
        }
    }
}
