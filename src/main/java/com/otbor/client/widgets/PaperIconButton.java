package com.otbor.client.widgets;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class PaperIconButton extends AbstractButton {

    private final String iconKey;
    private final String label;
    private final float baseRotationDeg;
    private final long appearDelay;
    private final Runnable action;

    private float hoverAnim = 0f;
    private long firstRender = -1L;
    private long lastMs = 0L;
    private long pressStart = 0L;

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
        pressStart = System.currentTimeMillis();
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
        float dt = lastMs == 0L ? 0f : Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        float elapsed = now - firstRender - appearDelay;
        float appear = PaperRender.easeOut(elapsed <= 0 ? 0f : Math.min(1f, elapsed / 260f));

        boolean hovered = this.active && this.isHoveredOrFocused();
        hoverAnim = PaperRender.approach(hoverAnim, hovered ? 1f : 0f, dt * 6f);
        float press = pressStart == 0L ? 0f : Mth.clamp(1f - (now - pressStart) / 180f, 0f, 1f);
        float time = now / 1000f;

        float sway = Mth.sin(time * 0.9f + baseRotationDeg * 3f) * 0.3f * (1f - hoverAnim);
        float rotation = baseRotationDeg * (1f - 0.4f * hoverAnim) + sway;
        float lift = -3f * hoverAnim + 1.5f * press;
        float slideIn = (1f - appear) * 18f;
        float scale = 0.96f + 0.04f * appear + 0.03f * hoverAnim - 0.03f * press;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        Font font = Minecraft.getInstance().font;

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f + slideIn + lift, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        gfx.pose().scale(scale, scale, 1f);
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        if (hoverAnim > 0.01f) PaperRender.drawSoftShadow(gfx, 0, 0, w, h, 4, 0.2f * hoverAnim);

        float brightness = this.active ? 0.95f + 0.08f * hoverAnim : 0.75f;
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, brightness, PaperRender.PAPER_BASE);
        PaperRender.drawTape(gfx, w / 2 - 11, -4, 22, 9, 0xB8);

        long seed = ((long) x * 31L) ^ ((long) y * 17L);
        PaperRender.drawInkStroke(gfx, 4, 3, w - 8, 1,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.55f + 0.45f * hoverAnim), seed);

        int iconPx = PixelIcons.scaleFor(w / 2) * 16;
        int gap = 5;
        int blockH = iconPx + gap + font.lineHeight;
        int top = (h - blockH) / 2;
        PixelIcons.drawButtonIcon(gfx, iconKey, w / 2, top + iconPx / 2, w / 2, hoverAnim);

        int labelY = top + iconPx + gap;
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
            gfx.fill(w - 10, 3, w - 3, 4, c);
            gfx.fill(w - 10, 3, w - 9, 7, c);
        }

        gfx.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}