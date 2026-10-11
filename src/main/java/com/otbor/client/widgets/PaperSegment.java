package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class PaperSegment extends AbstractButton {

    private final String[] options;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private float slide = -1f;
    private long lastMs = 0L;

    public PaperSegment(int x, int y, int w, int h, String[] options,
                         IntSupplier getter, IntConsumer setter) {
        super(x, y, w, h, Component.literal(""));
        this.options = options;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress() {
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !this.visible) return false;
        if (button == 0 && this.clicked(mx, my)) {
            int x = getX();
            int w = getWidth();
            int each = Math.max(1, w / options.length);
            int i = Math.max(0, Math.min(options.length - 1, (int) ((mx - x) / each)));
            setter.accept(i);
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 0f : Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int n = options.length;
        int selected = Math.max(0, Math.min(n - 1, getter.getAsInt()));
        int each = Math.max(1, w / n);
        if (slide < 0f) slide = selected;
        slide = PaperRender.approach(slide, selected, dt * 12f);

        var font = Minecraft.getInstance().font;
        int totalW = each * n - 2;

        PaperRender.drawRoundBox(gfx, x, y, totalW, h,
                PaperRender.mul(PaperRender.PAPER_DARK, 0.82f), PaperRender.INK_FADED, 0f);
        for (int i = 1; i < n; i++) {
            gfx.fill(x + i * each - 1, y + 3, x + i * each, y + h - 3,
                    PaperRender.withAlpha(PaperRender.INK_FADED, 0.35f));
        }

        if (isHovered() && mouseX >= x && mouseX < x + totalW) {
            int idx = Mth.clamp((mouseX - x) / each, 0, n - 1);
            if (idx != selected) {
                int sx = x + idx * each;
                gfx.fill(sx + 1, y + 2, sx + each - 3, y + h - 2,
                        PaperRender.withAlpha(PaperRender.PAPER_LIGHT, 0.28f));
            }
        }

        int tx = x + 1 + Math.round(slide * each);
        PaperRender.drawRoundBox(gfx, tx, y + 1, each - 4, h - 2, PaperRender.PAPER_LIGHT, PaperRender.INK, 0.3f);
        PaperRender.drawInkStroke(gfx, tx + 5, y + h - 4, Math.max(0, each - 14), 1,
                PaperRender.INK_RED, ((long) x * 31L) ^ ((long) y * 17L));

        for (int i = 0; i < n; i++) {
            float close = Mth.clamp(1f - Math.abs(i - slide), 0f, 1f);
            int color = PaperRender.mix(PaperRender.INK_FADED, PaperRender.INK, close);
            String label = options[i];
            int lw = font.width(label);
            PaperRender.drawInkText(gfx, font, label, x + i * each + (each - 2) / 2 - lw / 2,
                    y + (h - 8) / 2, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput n) {
        this.defaultButtonNarrationText(n);
    }
}
