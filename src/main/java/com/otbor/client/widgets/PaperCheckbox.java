package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PaperCheckbox extends AbstractButton {

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private static final int[][] TICK = {{2, 7}, {3, 8}, {4, 9}, {5, 10}, {6, 9}, {7, 8}, {8, 7}, {9, 6}, {10, 5}, {11, 4}};

    private float hoverAnim = 0f;
    private float checkAnim = -1f;
    private long lastMs = 0L;

    public PaperCheckbox(int x, int y, int w, int h, String label,
                         BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, w, h, Component.literal(label));
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress() {
        setter.accept(!getter.getAsBoolean());
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

        boolean checked = getter.getAsBoolean();
        if (checkAnim < 0f) checkAnim = checked ? 1f : 0f;
        checkAnim = PaperRender.approach(checkAnim, checked ? 1f : 0f, dt * 6f);
        boolean hovered = this.active && this.isHoveredOrFocused();
        hoverAnim = PaperRender.approach(hoverAnim, hovered ? 1f : 0f, dt * 7f);

        int x = getX();
        int y = getY();
        int bx = x;
        int by = y + (getHeight() - 14) / 2;

        int fill = PaperRender.mix(PaperRender.PAPER_LIGHT, 0xFFF6ECCB, hoverAnim);
        int border = PaperRender.mix(PaperRender.INK, PaperRender.INK_RED, hoverAnim);
        PaperRender.drawRoundBox(gfx, bx, by, 14, 14, fill, border, 0.3f);

        int shown = (int) Math.ceil(checkAnim * TICK.length);
        for (int i = 0; i < shown; i++) {
            int px = bx + TICK[i][0];
            int py = by + TICK[i][1];
            gfx.fill(px + 1, py + 1, px + 3, py + 3, 0x40000000);
        }
        for (int i = 0; i < shown; i++) {
            int px = bx + TICK[i][0];
            int py = by + TICK[i][1];
            gfx.fill(px, py, px + 2, py + 2, PaperRender.INK_RED);
        }

        var font = Minecraft.getInstance().font;
        int ty = y + (getHeight() - 8) / 2 + 1;
        int color = PaperRender.mix(PaperRender.INK, PaperRender.INK_RED, hoverAnim);
        PaperRender.drawInkText(gfx, font, getMessage(), x + 20, ty, color);
        if (hoverAnim > 0.01f) {
            int uw = (int) (font.width(getMessage()) * hoverAnim);
            PaperRender.drawInkStroke(gfx, x + 20, ty + 10, uw, 1,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.6f), ((long) x * 31L) ^ ((long) y * 17L));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput n) {
        this.defaultButtonNarrationText(n);
    }
}
