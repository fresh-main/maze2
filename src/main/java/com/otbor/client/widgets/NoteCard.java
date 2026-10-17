package com.otbor.client.widgets;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

public class NoteCard extends Button {

    public enum PaperVariant { BASE, LIGHT, DARK }

    private final int index;
    private final String iconKey;
    private final String subtitle;
    private final String sub2;
    private final PaperVariant variant;
    private final boolean brassPin;
    private final float baseRotationDeg;
    private final long appearDelay;
    private final int[] specX;
    private final int[] specY;
    private final int[] specSize;
    private final int[] specTone;

    private float hoverAnim = 0f;
    private float tilt = 0f;
    private float spin = 0f;
    private long firstRender = -1L;
    private long lastMs = 0L;
    private long pressStart = 0L;
    private float pinX;
    private float pinY;

    public NoteCard(int x, int y, int w, int h,
                    int index,
                    Component title, String subtitle, String sub2,
                    String iconKey,
                    PaperVariant variant,
                    boolean brassPin,
                    float baseRotationDeg,
                    long appearDelayMs,
                    OnPress onPress) {
        super(x, y, w, h, title, onPress, DEFAULT_NARRATION);
        this.index = index;
        this.iconKey = iconKey;
        this.subtitle = subtitle;
        this.sub2 = sub2;
        this.variant = variant;
        this.brassPin = brassPin;
        this.baseRotationDeg = baseRotationDeg;
        this.appearDelay = appearDelayMs;

        Random rnd = new Random(index * 7919L + w * 31L + h);
        int count = Math.max(12, (w * h) / 900);
        specX = new int[count];
        specY = new int[count];
        specSize = new int[count];
        specTone = new int[count];
        for (int i = 0; i < count; i++) {
            specX[i] = 3 + rnd.nextInt(Math.max(1, w - 6));
            specY[i] = 3 + rnd.nextInt(Math.max(1, h - 6));
            specSize[i] = rnd.nextInt(4) == 0 ? 2 : 1;
            specTone[i] = rnd.nextInt(3);
        }
        this.pinX = x + w / 2f;
        this.pinY = y + 4f;
    }

    public float getPinX() { return pinX; }
    public float getPinY() { return pinY; }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    @Override
    public void onPress() {
        pressStart = System.currentTimeMillis();
        super.onPress();
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        if (firstRender < 0) firstRender = now;
        float dt = lastMs == 0L ? 0f : Math.min(0.05f, (now - lastMs) / 1000f);
        lastMs = now;
        float elapsed = now - firstRender - appearDelay;
        float appear = PaperRender.easeOut(elapsed <= 0 ? 0f : Math.min(1f, elapsed / 320f));

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean hovered = this.active && this.isHoveredOrFocused();
        hoverAnim = PaperRender.approach(hoverAnim, hovered ? 1f : 0f, dt * 6f);
        float tiltTarget = hovered ? Mth.clamp((mouseX - (x + w / 2f)) / w, -0.5f, 0.5f) * 1.8f : 0f;
        tilt += (tiltTarget - tilt) * Math.min(1f, dt * 10f);
        spin += dt * (18f + 200f * hoverAnim);
        float press = pressStart == 0L ? 0f : Mth.clamp(1f - (now - pressStart) / 180f, 0f, 1f);
        float time = now / 1000f;

        float sway = Mth.sin(time * 0.9f + index * 1.7f) * 0.3f * (1f - hoverAnim);
        float rotation = baseRotationDeg * (1f - 0.4f * hoverAnim) + sway + tilt;
        float lift = -5f * hoverAnim + 2f * press;
        float slideIn = (1f - appear) * 24f;
        float scale = 0.97f + 0.03f * appear + 0.025f * hoverAnim - 0.02f * press;

        float rad = (float) Math.toRadians(rotation);
        float pinDy = (4f - h / 2f) * scale;
        pinX = x + w / 2f - pinDy * Mth.sin(rad);
        pinY = y + h / 2f + slideIn + lift + pinDy * Mth.cos(rad);

        Font font = Minecraft.getInstance().font;

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f + slideIn + lift, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        gfx.pose().scale(scale, scale, 1f);
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        if (hoverAnim > 0.01f) PaperRender.drawSoftShadow(gfx, 0, 0, w, h, 5, 0.22f * hoverAnim);

        int paperColor = variant == PaperVariant.LIGHT ? PaperRender.PAPER_LIGHT
                : variant == PaperVariant.DARK ? PaperRender.PAPER_DARK
                : PaperRender.PAPER_BASE;
        float brightness = 0.95f + 0.08f * hoverAnim;
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, brightness, paperColor);
        drawPaperDetails(gfx, w, h);
        PaperRender.drawPin(gfx, w / 2, 4, brassPin);
        if (hoverAnim > 0.01f) drawBrackets(gfx, w, h);

        float k = Mth.clamp(h / 220f, 0.72f, 1f);
        int iconSize = (int) (44 * k);
        int iconCy = (int) (32 * k);

        String idLabel = "N " + String.format("%02d", index + 1);
        gfx.drawString(font, idLabel, 12, 10, PaperRender.INK_FADED, false);
        String codeLetter = "[" + "ABCDE".charAt(index % 5) + "]";
        gfx.drawString(font, codeLetter, w - 12 - font.width(codeLetter), 10, PaperRender.INK_FADED, false);

        float lookX = Mth.clamp((mouseX - (x + w / 2f)) / 160f, -1f, 1f);
        float lookY = Mth.clamp((mouseY - (y + iconCy)) / 120f, -1f, 1f);
        PixelIcons.drawCardIcon(gfx, iconKey, w / 2, iconCy, iconSize, time, hoverAnim, spin, lookX, lookY);

        int titleY = iconCy + (int) (46 * k);
        int tw = Math.max(1, font.width(getMessage()));
        float titleScale = Math.min(1.6f, (w - 16f) / tw);
        int titleRenderW = (int) (tw * titleScale);
        gfx.pose().pushPose();
        gfx.pose().translate(w / 2f - titleRenderW / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        PaperRender.drawInkText(gfx, font, getMessage(), 0, 0, PaperRender.INK);
        gfx.pose().popPose();

        int lineY = titleY + (int) (9 * titleScale) + 6;
        int divW = (int) (100 + 24 * hoverAnim);
        PaperRender.drawHandDivider(gfx, w / 2 - divW / 2, lineY, divW,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.7f));

        int subY = lineY + 6;
        int subW = font.width(subtitle);
        gfx.drawString(font, subtitle, w / 2 - subW / 2, subY, PaperRender.INK_SOFT, false);

        int sub2Y = subY + 12;
        int sub2W = font.width(sub2);
        int maxW = w - 16;
        if (sub2W <= maxW) {
            gfx.drawString(font, sub2, w / 2 - sub2W / 2, sub2Y, PaperRender.INK_FADED, false);
        } else {
            float sub2Scale = (float) maxW / (float) sub2W;
            int sub2RenderW = (int) (sub2W * sub2Scale);
            gfx.pose().pushPose();
            gfx.pose().translate(w / 2f - sub2RenderW / 2f, sub2Y, 0);
            gfx.pose().scale(sub2Scale, sub2Scale, 1f);
            gfx.drawString(font, sub2, 0, 0, PaperRender.INK_FADED, false);
            gfx.pose().popPose();
        }

        if (hoverAnim > 0.05f) {
            String cta = Component.translatable("otbor.card.open").getString();
            int a = (int) (hoverAnim * 255);
            int ctaColor = (a << 24) | 0x7A1F1F;
            float ctaScale = 1.2f;
            int ctaRenderW = (int) (font.width(cta) * ctaScale);
            float bob = Mth.sin(time * 6f) * 1.5f * hoverAnim;
            gfx.pose().pushPose();
            gfx.pose().translate(w / 2f - ctaRenderW / 2f + bob, h - 20, 0);
            gfx.pose().scale(ctaScale, ctaScale, 1f);
            gfx.drawString(font, cta, 0, 0, ctaColor, false);
            gfx.pose().popPose();
        }

        gfx.pose().popPose();
    }

    private void drawPaperDetails(GuiGraphics gfx, int w, int h) {
        for (int i = 0; i < specX.length; i++) {
            int col = specTone[i] == 0 ? PaperRender.withAlpha(PaperRender.PAPER_DARK, 0.55f)
                    : specTone[i] == 1 ? PaperRender.withAlpha(PaperRender.PAPER_LIGHT, 0.65f)
                    : PaperRender.withAlpha(PaperRender.PAPER_STAIN, 0.12f);
            gfx.fill(specX[i], specY[i], specX[i] + specSize[i], specY[i] + specSize[i], col);
        }
        int crease = (int) (h * 0.64f);
        gfx.fill(6, crease, w - 6, crease + 1, PaperRender.withAlpha(PaperRender.INK, 0.07f));
        gfx.fill(6, crease + 1, w - 6, crease + 2, PaperRender.withAlpha(PaperRender.PAPER_LIGHT, 0.30f));
        if (variant == PaperVariant.LIGHT) {
            PaperRender.drawRing(gfx, w - 34, h - 38, 14, PaperRender.withAlpha(PaperRender.PAPER_STAIN, 0.16f));
            PaperRender.drawRing(gfx, w - 34, h - 38, 13, PaperRender.withAlpha(PaperRender.PAPER_STAIN, 0.08f));
        }
    }

    private void drawBrackets(GuiGraphics gfx, int w, int h) {
        int c = PaperRender.withAlpha(PaperRender.INK_RED, 0.85f * hoverAnim);
        int len = 7;
        gfx.fill(4, 4, 4 + len, 5, c);
        gfx.fill(4, 4, 5, 4 + len, c);
        gfx.fill(w - 4 - len, 4, w - 4, 5, c);
        gfx.fill(w - 5, 4, w - 4, 4 + len, c);
        gfx.fill(4, h - 5, 4 + len, h - 4, c);
        gfx.fill(4, h - 4 - len, 5, h - 4, c);
        gfx.fill(w - 4 - len, h - 5, w - 4, h - 4, c);
        gfx.fill(w - 5, h - 4 - len, w - 4, h - 4, c);
    }




}
