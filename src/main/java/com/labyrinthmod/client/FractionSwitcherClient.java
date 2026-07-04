package com.labyrinthmod.client;

import com.labyrinthmod.client.gui.FractionSwitchOverlay;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.SwitchFractionPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

// 1. Регистрация GUI оверлея (MOD Bus)
// УБРАЛИ public!
@Mod.EventBusSubscriber(modid = "labyrinthmod", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
class FractionOverlayRegistration {
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("fraction_switch_overlay", FractionSwitchOverlay.OVERLAY);
    }
}

// 2. Обработка клавиш F3+F6 (FORGE Bus)
// УБРАЛИ public!
@Mod.EventBusSubscriber(modid = "labyrinthmod", value = Dist.CLIENT)
class FractionKeyHandler {

    private static boolean f3WasPressed = false;
    private static boolean f6WasPressed = false;
    private static boolean overlayActive = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null) return;
        if (mc.getWindow() == null) return;

        long window = mc.getWindow().getWindow();
        // Опрос состояния клавиш напрямую через GLFW (обходит конфликты с экраном отладки F3)
        boolean f3Pressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
        boolean f6Pressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F6) == GLFW.GLFW_PRESS;

        // Если F3 зажата и F6 нажата ВПЕРВЫЕ (не была зажата в прошлом тике)
        if (f3Pressed && f6Pressed && !f6WasPressed) {
            if (!overlayActive) {
                // Первый тап F6 при зажатой F3 -> открываем GUI
                FractionSwitchOverlay.showOverlay();
                overlayActive = true;
            } else {
                // Повторные тапы F6 -> переключаем фракцию в GUI
                FractionSwitchOverlay.nextFraction();
            }
        }

        // Если отпустили F3 и GUI было открыто -> применяем выбор
        if (!f3Pressed && overlayActive) {
            applyFractionSelection();
            overlayActive = false;
            FractionSwitchOverlay.hideOverlay();
        }

        // Сохраняем состояния для следующего тика
        f3WasPressed = f3Pressed;
        f6WasPressed = f6Pressed;
    }

    private static void applyFractionSelection() {
        // Отправляем пакет на сервер с выбранной фракцией
        NetworkHandler.CHANNEL.sendToServer(new SwitchFractionPacket(
                FractionSwitchOverlay.getSelectedFraction()
        ));
    }
}