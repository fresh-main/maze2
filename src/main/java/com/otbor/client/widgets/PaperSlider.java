package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class PaperSlider extends AbstractSliderButton {

    private final DoubleConsumer setter;
    private final DoubleFunction<String> labeler;
    private final String title;
    private float hoverAnim = 0f;
    private long lastMs = 0L;
    /** Если true — setter вызывается ТОЛЬКО на release слайдера (mouseUp). Иначе — на каждое
     *  изменение value во время drag. Для тяжёлых настроек (renderDistance, graphicsMode,
     *  framerate, gamma, particles, clouds) обязателен deferred-режим: их setter
     *  триггерит chunk reload / rebuild meshes / reload resource pack, что при быстром
     *  drag-движении даёт серию N перезагрузок и подвисание клиента. */
    private final boolean applyOnReleaseOnly;
    private boolean dirty;

    public PaperSlider(int x, int y, int w, int h, String title, double initialNormalized,
                       DoubleConsumer setter, DoubleFunction<String> labeler) {
        this(x, y, w, h, title, initialNormalized, setter, labeler, false);
    }

    public PaperSlider(int x, int y, int w, int h, String title, double initialNormalized,
                       DoubleConsumer setter, DoubleFunction<String> labeler,
                       boolean applyOnReleaseOnly) {
        super(x, y, w, h, Component.literal(title), clamp01(initialNormalized));
        this.title = title;
        this.setter = setter;
        this.labeler = labeler;
        this.applyOnReleaseOnly = applyOnReleaseOnly;
        this.updateMessage();
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.literal(title + "  " + labeler.apply(this.value)));
    }

    @Override
    protected void applyValue() {
        if (applyOnReleaseOnly) {
            // Запоминаем что изменилось — реальный setter дёрнем в onRelease.
            dirty = true;
        } else {
            setter.accept(this.value);
        }
    }

    @Override
    public void onRelease(double mx, double my) {
        super.onRelease(mx, my);
        if (applyOnReleaseOnly && dirty) {
            setter.accept(this.value);
            dirty = false;
        }
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 0f : Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        boolean hover = this.active && this.isHoveredOrFocused();
        hoverAnim = PaperRender.approach(hoverAnim, hover ? 1f : 0f, dt * 7f);

        int x = getX();
        int y = getY();
        int w = getWidth();
        var font = Minecraft.getInstance().font;

        String v = labeler.apply(this.value);
        int vw = font.width(v);
        PaperRender.drawInkText(gfx, font, title, x, y, PaperRender.INK);
        PaperRender.drawInkText(gfx, font, v, x + w - vw, y,
                PaperRender.mix(PaperRender.INK_SOFT, PaperRender.INK_RED, hoverAnim));

        int cy = y + 17;
        for (int k = 0; k <= 4; k++) {
            int tx = x + 4 + (w - 9) * k / 4;
            gfx.fill(tx, cy - 6, tx + 1, cy - 4, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.4f));
        }

        PaperRender.drawRoundBox(gfx, x, cy - 3, w, 6, PaperRender.mul(PaperRender.PAPER_DARK, 0.85f), PaperRender.INK_FADED, 0f);

        int kx = x + 4 + (int) ((w - 9) * this.value);
        if (kx > x + 2) {
            gfx.fill(x + 2, cy - 1, kx, cy + 1, PaperRender.INK_RED);
            gfx.fill(x + 2, cy - 1, kx, cy, PaperRender.mix(PaperRender.INK_RED, 0xFFE06060, 0.4f));
        }

        gfx.pose().pushPose();
        gfx.pose().translate(kx, cy - hoverAnim, 0);
        float s = 1f + 0.15f * hoverAnim;
        gfx.pose().scale(s, s, 1f);
        PaperRender.drawRoundBox(gfx, -4, -6, 8, 12,
                PaperRender.mix(PaperRender.PAPER_LIGHT, 0xFFF6ECCB, hoverAnim), PaperRender.INK, 0.4f);
        gfx.fill(-1, -3, 1, 3, PaperRender.INK_RED);
        gfx.pose().popPose();
    }
}
