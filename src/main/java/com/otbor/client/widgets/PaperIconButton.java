package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class PaperIconButton extends AbstractButton {

    private static final String[] POWER = {
            ".....##.....",
            ".....##.....",
            "..#..##..#..",
            ".##..##..##.",
            "##...##...##",
            "##........##",
            "##........##",
            "##........##",
            ".##......##.",
            "..##....##..",
            "...######..."
    };
    private static final String[] GLOBE = {
            "...######...",
            ".##.#..#.##.",
            ".#..#..#..#.",
            "#...#..#...#",
            "############",
            "#...#..#...#",
            "#...#..#...#",
            "############",
            ".#..#..#..#.",
            ".##.#..#.##.",
            "...######..."
    };

    private final String iconKey;
    private final String label;
    private final float baseRotationDeg;
    private final long appearDelay;
    private final Runnable action;

    private float hoverAnim = 0f;
    private long firstRender = -1L;

    public PaperIconButton(int x, int y, int size, String iconKey, String label,
                           float baseRotationDeg, long appearDelayMs, Runnable action) {
        super(x, y, size, size, Component.literal(label));
        this.iconKey = iconKey;
        this.label = label;
        this.baseRotationDeg = baseRotationDeg;
        this.appearDelay = appearDelayMs;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        if (firstRender < 0) firstRender = now;
        long elapsed = now - firstRender - appearDelay;
        float appear = PaperRender.easeOut(elapsed <= 0 ? 0f : Math.min(1f, elapsed / 260f));

        boolean hovered = this.active && this.isHoveredOrFocused();
        float step = partialTick * 0.18f;
        hoverAnim = hovered ? Math.min(1f, hoverAnim + step) : Math.max(0f, hoverAnim - step);

        float lift = -3f * hoverAnim;
        float slideIn = (1f - appear) * 18f;
        float rotation = baseRotationDeg * (1f - 0.4f * hoverAnim);
        float scale = 0.96f + 0.04f * appear + 0.02f * hoverAnim;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        Font font = Minecraft.getInstance().font;

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f + slideIn + lift, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
        gfx.pose().scale(scale, scale, 1f);
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        float brightness = this.active ? 0.95f + 0.08f * hoverAnim : 0.75f;
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, brightness, PaperRender.PAPER_BASE);

        long seed = ((long) x * 31L) ^ ((long) y * 17L);
        PaperRender.drawInkStroke(gfx, 4, 2, w - 8, 1,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.55f + 0.45f * hoverAnim), seed);

        String[] bmp = "power".equals(iconKey) ? POWER : GLOBE;
        int unit = Math.max(1, w / 24);
        int iconW = bmp[0].length() * unit;
        int iconH = bmp.length * unit;
        int gap = 6;
        int blockH = iconH + gap + font.lineHeight;
        int top = (h - blockH) / 2;
        int ix = (w - iconW) / 2;
        int iconColor = PaperRender.mix(PaperRender.INK, PaperRender.INK_RED, hoverAnim);
        for (int row = 0; row < bmp.length; row++) {
            for (int col = 0; col < bmp[row].length(); col++) {
                if (bmp[row].charAt(col) == '#') {
                    int px = ix + col * unit;
                    int py = top + row * unit;
                    gfx.fill(px, py, px + unit, py + unit, iconColor);
                }
            }
        }

        int labelY = top + iconH + gap;
        int maxW = w - 8;
        int lw = font.width(label);
        float labelScale = lw > maxW ? (float) maxW / lw : 1f;
        int renderW = (int) (lw * labelScale);
        int textColor = this.active ? PaperRender.INK_DARK : 0xFF7A6A4E;
        gfx.pose().pushPose();
        gfx.pose().translate(w / 2f - renderW / 2f, labelY, 0);
        gfx.pose().scale(labelScale, labelScale, 1f);
        PaperRender.drawInkText(gfx, font, label, 0, 0, textColor);
        gfx.pose().popPose();

        if (hoverAnim > 0.01f) {
            int uw = (int) ((renderW + 6) * hoverAnim);
            PaperRender.drawInkStroke(gfx, w / 2 - uw / 2, labelY + 10, uw, 1,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.9f), seed + 1);

            int a = (int) (hoverAnim * 180);
            int c = (a << 24) | (PaperRender.INK_RED & 0xFFFFFF);
            gfx.fill(w - 10, 2, w - 3, 3, c);
            gfx.fill(w - 10, 2, w - 9, 6, c);
        }

        gfx.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}