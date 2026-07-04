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
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        var font = Minecraft.getInstance().font;
        String t = title;
        String v = labeler.apply(this.value);
        gfx.drawString(font, t, x, y, PaperRender.INK, false);
        int vw = font.width(v);
        gfx.drawString(font, v, x + w - vw, y, PaperRender.INK_SOFT, false);

        int barY = y + 12;
        int barH = 10;

        PaperRender.drawHandDivider(gfx, x, barY + barH / 2, w,
                PaperRender.withAlpha(PaperRender.INK, 0.55f));

        int fillW = Math.max(0, (int) ((w - 1) * this.value));
        for (int i = 0; i < fillW; i += 3) {
            int k = Math.min(3, fillW - i);
            gfx.fill(x + i, barY + barH / 2 - 1, x + i + k, barY + barH / 2, PaperRender.INK_RED);
        }

        for (int k = 0; k <= 4; k++) {
            int tx = x + (w - 1) * k / 4;
            gfx.fill(tx, barY + barH / 2 - 3, tx + 1, barY + barH / 2 + 3,
                    PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));
        }

        int knobX = x + (int) ((w - 1) * this.value);
        int knobY = barY + barH / 2;
        boolean hover = isHovered();
        int knobSize = hover ? 8 : 7;
        gfx.fill(knobX - knobSize + 1, knobY - knobSize + 1, knobX + knobSize + 1, knobY + knobSize + 1, 0x50000000);
        gfx.fill(knobX - knobSize, knobY - knobSize, knobX + knobSize, knobY + knobSize, PaperRender.PAPER_LIGHT);
        gfx.fill(knobX - knobSize, knobY - knobSize, knobX + knobSize, knobY - knobSize + 1, PaperRender.INK);
        gfx.fill(knobX - knobSize, knobY + knobSize - 1, knobX + knobSize, knobY + knobSize, PaperRender.INK);
        gfx.fill(knobX - knobSize, knobY - knobSize, knobX - knobSize + 1, knobY + knobSize, PaperRender.INK);
        gfx.fill(knobX + knobSize - 1, knobY - knobSize, knobX + knobSize, knobY + knobSize, PaperRender.INK);
        gfx.fill(knobX - 2, knobY - 2, knobX + 2, knobY + 2, PaperRender.INK_RED);
    }
}
