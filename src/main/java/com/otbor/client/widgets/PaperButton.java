package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class PaperButton extends Button {

    private float hoverAnim = 0f;
    private float appearAnim = 0f;
    private long firstRender = -1L;
    private final long appearDelay;
    private int accentColor = PaperRender.INK_RED;
    private float rotationDeg = 0f;

    public PaperButton(int x, int y, int w, int h, Component msg, OnPress action) {
        this(x, y, w, h, msg, action, 0);
    }

    public PaperButton(int x, int y, int w, int h, Component msg, OnPress action, long appearDelayMs) {
        super(x, y, w, h, msg, action, DEFAULT_NARRATION);
        this.appearDelay = appearDelayMs;
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    public PaperButton withAccent(int color) {
        this.accentColor = color;
        return this;
    }

    public PaperButton withRotation(float deg) {
        this.rotationDeg = deg;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        if (firstRender < 0) firstRender = now;
        long elapsed = now - firstRender - appearDelay;
        appearAnim = elapsed <= 0 ? 0f : Math.min(1f, elapsed / 260f);

        boolean active = this.active;
        boolean hovered = active && this.isHoveredOrFocused();
        float step = partialTick * 0.18f;
        hoverAnim = hovered ? Math.min(1f, hoverAnim + step) : Math.max(0f, hoverAnim - step);

        float appear = PaperRender.easeOut(appearAnim);
        float lift = -2.5f * hoverAnim;
        float slideIn = (1f - appear) * 18f;
        float scale = 0.96f + 0.04f * appear + 0.02f * hoverAnim;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f + slideIn + lift, 0);
        if (rotationDeg != 0f) {
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDeg));
        }
        gfx.pose().scale(scale, scale, 1f);
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        float brightness = active ? (0.92f + 0.10f * hoverAnim) : 0.75f;
        // drawPaperCard вместо drawPaper: на маленьких кнопках разницы визуально нет,
        // а fill-вызовов в десятки раз меньше (актуально для экранов с 4+ кнопками).
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, brightness, PaperRender.PAPER_BASE);

        long seed = ((long) x * 31L) ^ ((long) y * 17L);
        int topInkColor = PaperRender.withAlpha(PaperRender.INK_SOFT, 0.55f + 0.45f * hoverAnim);
        PaperRender.drawInkStroke(gfx, 4, 2, w - 8, 1, topInkColor, seed);

        Font font = Minecraft.getInstance().font;
        int textColor = active ? PaperRender.INK_DARK : 0xFF7A6A4E;
        int textX = w / 2 - font.width(getMessage()) / 2;
        int textY = (h - 8) / 2;
        PaperRender.drawInkText(gfx, font, getMessage(), textX, textY, textColor);

        if (hoverAnim > 0.01f) {
            int fullW = font.width(getMessage()) + 10;
            int uw = (int) (fullW * hoverAnim);
            int ux = w / 2 - uw / 2;
            int uy = textY + 10;
            PaperRender.drawInkStroke(gfx, ux, uy, uw, 1, PaperRender.withAlpha(accentColor, 0.9f), seed + 1);
        }

        if (hoverAnim > 0.01f) {
            int a = (int) (hoverAnim * 180);
            gfx.fill(w - 10, 2, w - 3, 3, (a << 24) | (accentColor & 0xFFFFFF));
            gfx.fill(w - 10, 2, w - 9, 6, (a << 24) | (accentColor & 0xFFFFFF));
        }

        gfx.pose().popPose();
    }
}
