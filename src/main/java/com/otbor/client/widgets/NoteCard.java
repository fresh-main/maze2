package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public class NoteCard extends Button {

    public enum PaperVariant { BASE, LIGHT, DARK }

    private final int index;
    private final String iconKey;
    private final String subtitle;
    private final String sub2;
    private final PaperVariant variant;
    private final boolean brassPin;
    private final float baseRotationDeg;

    private float hoverAnim = 0f;
    private float appearAnim = 0f;
    private long firstRender = -1L;
    private final long appearDelay;

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
        appearAnim = elapsed <= 0 ? 0f : Math.min(1f, elapsed / 320f);

        boolean hovered = this.active && this.isHoveredOrFocused();
        float step = partialTick * 0.18f;
        hoverAnim = hovered ? Math.min(1f, hoverAnim + step) : Math.max(0f, hoverAnim - step);

        float appear = PaperRender.easeOut(appearAnim);
        float lift = -4f * hoverAnim;
        float slideIn = (1f - appear) * 22f;
        float rotation = baseRotationDeg * (1f - 0.4f * hoverAnim);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f + slideIn + lift, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        int paperColor = variant == PaperVariant.LIGHT ? PaperRender.PAPER_LIGHT
                : variant == PaperVariant.DARK  ? PaperRender.PAPER_DARK
                : PaperRender.PAPER_BASE;
        float brightness = 0.95f + 0.08f * hoverAnim;
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, brightness, paperColor);

        PaperRender.drawPin(gfx, w / 2, 4, brassPin);

        Font font = Minecraft.getInstance().font;

        String idLabel = "N " + String.format("%02d", index + 1);
        gfx.drawString(font, idLabel, 10, 8, PaperRender.INK_FADED, false);
        String codeLetter = "[" + "ABCDE".charAt(index % 5) + "]";
        gfx.drawString(font, codeLetter, w - 10 - font.width(codeLetter), 8, PaperRender.INK_FADED, false);

        int iconY = 24;
        drawIcon(gfx, iconKey, w / 2, iconY, 44);

        int titleY = iconY + 46;
        float titleScale = 1.6f;
        int titleText_w = (int) (font.width(getMessage()) * titleScale);
        gfx.pose().pushPose();
        gfx.pose().translate(w / 2f - titleText_w / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        gfx.drawString(font, getMessage(), 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        int lineY = titleY + (int) (9 * titleScale) + 6;
        PaperRender.drawHandDivider(gfx, w / 2 - 50, lineY, 100,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.7f));

        int subY = lineY + 6;
        int subW = font.width(subtitle);
        gfx.drawString(font, subtitle, w / 2 - subW / 2, subY,
                PaperRender.INK_SOFT, false);

        int sub2Y = subY + 12;
        int sub2W = font.width(sub2);
        int maxW = w - 16;
        if (sub2W <= maxW) {
            gfx.drawString(font, sub2, w / 2 - sub2W / 2, sub2Y,
                    PaperRender.INK_FADED, false);
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
            String cta = "-> ОТКРЫТЬ <-";
            int a = (int) (hoverAnim * 255);
            int ctaColor = (a << 24) | 0x7A1F1F;
            int ctaW = font.width(cta);
            float ctaScale = 1.2f;
            int ctaRenderW = (int) (ctaW * ctaScale);
            gfx.pose().pushPose();
            gfx.pose().translate(w / 2f - ctaRenderW / 2f, h - 18, 0);
            gfx.pose().scale(ctaScale, ctaScale, 1f);
            gfx.drawString(font, cta, 0, 0, ctaColor, false);
            gfx.pose().popPose();
        }

        gfx.pose().popPose();
    }

    private static void drawIcon(GuiGraphics gfx, String key, int cx, int cy, int size) {
        int s = size;
        int ink = PaperRender.INK;
        int soft = PaperRender.INK_SOFT;
        int red = PaperRender.INK_RED;
        switch (key) {
            case "door": {
                gfx.fill(cx - s/3, cy - s/2, cx + s/3, cy - s/2 + 1, ink);
                gfx.fill(cx - s/3, cy + s/2 - 1, cx + s/3, cy + s/2, ink);
                gfx.fill(cx - s/3, cy - s/2, cx - s/3 + 1, cy + s/2, ink);
                gfx.fill(cx + s/3 - 1, cy - s/2, cx + s/3, cy + s/2, ink);
                gfx.fill(cx - s/3 + 3, cy - s/2 + 3, cx + s/3 - 3, cy - s/2 + 4, soft);
                gfx.fill(cx - s/3 + 3, cy + s/2 - 4, cx + s/3 - 3, cy + s/2 - 3, soft);
                gfx.fill(cx - s/3 + 3, cy - s/2 + 3, cx - s/3 + 4, cy + s/2 - 3, soft);
                gfx.fill(cx + s/3 - 4, cy - s/2 + 3, cx + s/3 - 3, cy + s/2 - 3, soft);
                gfx.fill(cx + s/6, cy, cx + s/6 + 2, cy + 2, ink);
                break;
            }
            case "compass": {
                int r = s / 2; // Радиус компаса

                // 1. Внешний корпус (двойное кольцо)
                drawCircleOutline(gfx, cx, cy, r, PaperRender.INK);
                drawCircleOutline(gfx, cx, cy, r - 2, PaperRender.INK_SOFT);

                // 2. Фон циферблата
                for (int dy = -r + 3; dy <= r - 3; dy++) {
                    for (int dx = -r + 3; dx <= r - 3; dx++) {
                        if (dx * dx + dy * dy <= (r - 3) * (r - 3)) {
                            gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, PaperRender.PAPER_LIGHT);
                        }
                    }
                }

                // 3. Деления по краям (N, S, E, W)
                int edge = r - 3;
                gfx.fill(cx - 2, cy - edge, cx + 3, cy - edge + 3, PaperRender.INK); // Север (N)
                gfx.fill(cx - 2, cy + edge - 3, cx + 3, cy + edge, PaperRender.INK); // Юг (S)
                gfx.fill(cx - edge, cy - 2, cx - edge + 3, cy + 3, PaperRender.INK); // Запад (W)
                gfx.fill(cx + edge - 3, cy - 2, cx + edge, cy + 3, PaperRender.INK); // Восток (E)

                // 4. Стрелка компаса (ромбовидная)
                int len = r - 5;
                int halfW = 3;

                // Северная часть (красная)
                for (int i = 0; i <= len; i++) {
                    int w = (i * halfW) / len;
                    if (w == 0) w = 1;
                    gfx.fill(cx - w, cy - i, cx + w + 1, cy - i + 1, PaperRender.INK_RED);
                }

                // Южная часть (светлая/белая)
                for (int i = 1; i <= len; i++) {
                    int w = (i * halfW) / len;
                    if (w == 0) w = 1;
                    gfx.fill(cx - w, cy + i - 1, cx + w + 1, cy + i, PaperRender.INK_SOFT);
                }

                // 5. Центральная ось (шарнир)
                gfx.fill(cx - 2, cy - 2, cx + 3, cy + 3, PaperRender.INK);
                gfx.fill(cx - 1, cy - 1, cx + 2, cy + 2, PaperRender.PAPER_LIGHT);

                break;
            }
            case "eye": {
                for (int i = -s/2; i <= s/2; i++) {
                    double t = i / (double)(s/2);
                    int dy = (int) Math.round(Math.sqrt(1 - t*t) * (s/3));
                    gfx.fill(cx + i, cy - dy, cx + i + 1, cy - dy + 1, ink);
                    gfx.fill(cx + i, cy + dy - 1, cx + i + 1, cy + dy, ink);
                }
                int ir = s / 5;
                for (int dy = -ir; dy <= ir; dy++) {
                    for (int dx = -ir; dx <= ir; dx++) {
                        if (dx*dx + dy*dy <= ir*ir && dx*dx + dy*dy >= (ir-2)*(ir-2)) {
                            gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, ink);
                        }
                    }
                }
                int pr = 3;
                for (int dy = -pr; dy <= pr; dy++) {
                    for (int dx = -pr; dx <= pr; dx++) {
                        if (dx*dx + dy*dy <= pr*pr) {
                            gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, red);
                        }
                    }
                }
                break;
            }
            case "gear": {
                int outer = s / 2;
                int inner = s / 3;
                int hub = s / 7;
                int[] ax = { 0, 1, 1, 1, 0, -1, -1, -1 };
                int[] ay = { -1, -1, 0, 1, 1, 1, 0, -1 };
                int tooth = 5;
                for (int i = 0; i < 8; i++) {
                    int tx = cx + ax[i] * (outer - 2) - tooth/2;
                    int ty = cy + ay[i] * (outer - 2) - tooth/2;
                    gfx.fill(tx, ty, tx + tooth, ty + tooth, ink);
                }
                for (int dy = -inner; dy <= inner; dy++) {
                    for (int dx = -inner; dx <= inner; dx++) {
                        int d2 = dx*dx + dy*dy;
                        if (d2 <= inner*inner && d2 >= (inner-2)*(inner-2)) {
                            gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, ink);
                        }
                    }
                }
                for (int dy = -hub; dy <= hub; dy++) {
                    for (int dx = -hub; dx <= hub; dx++) {
                        if (dx*dx + dy*dy <= hub*hub) {
                            gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, soft);
                        }
                    }
                }
                break;
            }
        }
    }

    private static void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int r, int color) {
        int x = r, y = 0, err = 0;
        while (x >= y) {
            gfx.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color);
            gfx.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, color);
            gfx.fill(cx - y, cy + x, cx - y + 1, cy + x + 1, color);
            gfx.fill(cx - x, cy + y, cx - x + 1, cy + y + 1, color);
            gfx.fill(cx - x, cy - y, cx - x + 1, cy - y + 1, color);
            gfx.fill(cx - y, cy - x, cx - y + 1, cy - x + 1, color);
            gfx.fill(cx + y, cy - x, cx + y + 1, cy - x + 1, color);
            gfx.fill(cx + x, cy - y, cx + x + 1, cy - y + 1, color);
            y++;
            if (err <= 0) err += 2 * y + 1;
            if (err > 0) { x--; err -= 2 * x + 1; }
        }
    }
}