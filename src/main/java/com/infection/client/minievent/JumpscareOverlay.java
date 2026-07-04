package com.infection.client.minievent;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Стробоскоп-вспышка: пока активен ACTIVE-jumpscare у любого админа, экран
 * быстро мигает белым на ПОЛНОЙ интенсивности БЕЗ ЗАТУХАНИЯ.
 *
 * Когда сервер шлёт IDLE (исчезновение админа в дым) — мерцание прекращается
 * МОМЕНТАЛЬНО (jumpscareProgress() возвращает -1, метод сразу выходит).
 */
public final class JumpscareOverlay implements IGuiOverlay {

    public static final JumpscareOverlay INSTANCE = new JumpscareOverlay();

    private JumpscareOverlay() {}

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                       GuiGraphics gfx,
                       float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        float progress = ClientMiniEventState.jumpscareProgress();
        if (progress < 0f) return; // мгновенный стоп при IDLE

        // Стробоскоп ~12 Hz. Полная амплитуда без decay — фиксированная всю фазу ACTIVE.
        long now = System.currentTimeMillis();
        boolean strobeOn = (now / 80L) % 2L == 0L;

        RenderSystem.enableBlend();

        if (strobeOn) {
            // Яркая белая вспышка
            gfx.fill(0, 0, screenW, screenH, 0xDCFFFFFF);
        } else {
            // Лёгкое подложечное затемнение между вспышками — НЕ полная тьма.
            gfx.fill(0, 0, screenW, screenH, 0x28000000);
        }

        // Постоянные боковые градиенты-фотовспышки (без затухания).
        int sideAlpha = 200;
        int side = (sideAlpha << 24) | 0xFFFFFF;
        int band = 28;
        gfx.fillGradient(0, 0, screenW, band, side, 0);
        gfx.fillGradient(0, screenH - band, screenW, screenH, 0, side);
    }
}
