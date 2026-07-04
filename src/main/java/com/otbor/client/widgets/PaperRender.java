package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Random;

public final class PaperRender {

    public static final int PAPER_LIGHT  = 0xFFE9DCB9;
    public static final int PAPER_BASE   = 0xFFD4C5A0;
    public static final int PAPER_DARK   = 0xFFB8A581;
    public static final int PAPER_STAIN  = 0xFF6B4423;
    public static final int PAPER_EDGE   = 0xFF8B7355;

    public static final int INK          = 0xFF2A1810;
    public static final int INK_DARK     = INK;
    public static final int INK_SOFT     = 0xFF3A2F20;
    public static final int INK_FADED    = 0xFF6B5842;
    public static final int INK_RED      = 0xFF7A1F1F;
    public static final int INK_BLOOD    = 0xFF5A1414;
    public static final int INK_RED_DIM  = 0xFFB83232;

    public static final int BOARD_1      = 0xFF1A1410;
    public static final int BOARD_2      = 0xFF0D0906;
    public static final int BOARD_3      = 0xFF2A1F15;

    public static final int PIN_RED      = 0xFF8B1A1A;
    public static final int PIN_BRASS    = 0xFFA88850;
    public static final int TAPE         = 0xFFD9C88F;

    private PaperRender() {}

    public static void drawBoardBackground(GuiGraphics gfx, int w, int h) {
        gfx.fill(0, 0, w, h, 0xFF0F0B07);
        int stripe = 120;
        Random r = new Random(0xC09B0A9DL);
        for (int x = 0; x < w; x += stripe) {
            int tone = r.nextInt(3);
            int col = tone == 0 ? BOARD_1 : (tone == 1 ? BOARD_2 : BOARD_3);
            gfx.fill(x, 0, Math.min(w, x + stripe), h, col);
            gfx.fill(x, 0, x + 1, h, 0xFF000000);
        }
        int vignetteH = Math.min(80, h / 4);
        for (int i = 0; i < vignetteH; i++) {
            int a = (int) (140 * (1 - i / (float) vignetteH));
            gfx.fill(0, i, w, i + 1, a << 24);
            gfx.fill(0, h - i - 1, w, h - i, a << 24);
        }
        int vignetteW = Math.min(80, w / 6);
        for (int i = 0; i < vignetteW; i++) {
            int a = (int) (110 * (1 - i / (float) vignetteW));
            gfx.fill(i, 0, i + 1, h, a << 24);
            gfx.fill(w - i - 1, 0, w - i, h, a << 24);
        }
    }

    public static void drawPaper(GuiGraphics gfx, int x, int y, int w, int h, float brightness) {
        drawPaper(gfx, x, y, w, h, brightness, PAPER_BASE);
    }

    /**
     * Лёгкая версия {@link #drawPaper} для grid-сценариев (сотни карточек, скролл, анимации).
     * Никаких seeded-Random пятен, tornEdge, многослойных обводок — только тень, тело,
     * 1-пиксельная рамка по краю и неброский внутренний штрих. ~6-7 fill-вызовов вместо ~200.
     * Подходит для карточек 100×100 ÷ 250×250.
     */
    public static void drawPaperCard(GuiGraphics gfx, int x, int y, int w, int h,
                                     float brightness, int paperColor) {
        int base   = mul(paperColor, brightness);
        int border = mul(PAPER_EDGE, brightness);
        int inner  = mul(PAPER_DARK, brightness);

        // тень
        gfx.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x60000000);
        // тело
        gfx.fill(x, y, x + w, y + h, base);
        // тёмная 1px рамка
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);
        // лёгкая внутренняя «оживляющая» полоска у верха — даёт ощущение текстуры без рандома
        gfx.fill(x + 2, y + 1, x + w - 2, y + 2, withAlpha(inner, 0.25f));
    }

    public static void drawPaper(GuiGraphics gfx, int x, int y, int w, int h, float brightness, int paperColor) {
        int base   = mul(paperColor, brightness);
        int light  = mul(PAPER_LIGHT, brightness);
        int dark   = mul(PAPER_DARK, brightness);
        int edge   = mul(PAPER_EDGE, brightness);

        gfx.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x70000000);
        gfx.fill(x + 4, y + 6, x + w + 4, y + h + 6, 0x30000000);

        gfx.fill(x, y, x + w, y + h, base);

        Random rnd = new Random(((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) w * 83492791L) ^ ((long) h));
        int spots = Math.max(6, (w * h) / 700);
        for (int i = 0; i < spots; i++) {
            int sx = x + 2 + rnd.nextInt(Math.max(1, w - 4));
            int sy = y + 2 + rnd.nextInt(Math.max(1, h - 4));
            int tone = rnd.nextInt(4);
            int col = tone == 0 ? light : (tone == 1 ? dark : (tone == 2 ? mix(base, edge, 0.4f) : PAPER_STAIN | 0x30000000));
            int sz = rnd.nextInt(3) == 0 ? 2 : 1;
            gfx.fill(sx, sy, sx + sz, sy + sz, col);
        }

        for (int i = 0; i < 3; i++) {
            int sx = x + 4 + rnd.nextInt(Math.max(1, w - 8));
            int sy = y + 4 + rnd.nextInt(Math.max(1, h - 8));
            int size = 1 + rnd.nextInt(2);
            gfx.fill(sx, sy, sx + size, sy + size, 0x55000000);
        }

        for (int i = 0; i < 6; i++) {
            int a = (int) (40 * (1 - i / 6f));
            int col = (a << 24);
            gfx.fill(x, y + i, x + w, y + i + 1, col);
            gfx.fill(x, y + h - i - 1, x + w, y + h - i, col);
            gfx.fill(x + i, y, x + i + 1, y + h, col);
            gfx.fill(x + w - i - 1, y, x + w - i, y + h, col);
        }

        drawTornEdge(gfx, rnd, x, y, w, h, base, edge);
    }

    private static void drawTornEdge(GuiGraphics gfx, Random rnd, int x, int y, int w, int h, int base, int edge) {
        int darken = 0xFF000000 | (edge & 0x00FFFFFF);
        for (int i = 0; i < w; i++) {
            if (rnd.nextInt(4) == 0) gfx.fill(x + i, y, x + i + 1, y + 1, 0x00000000);
            if (rnd.nextInt(4) == 0) gfx.fill(x + i, y + h - 1, x + i + 1, y + h, 0x00000000);
            if (rnd.nextInt(5) == 0) gfx.fill(x + i, y + 1, x + i + 1, y + 2, darken);
            if (rnd.nextInt(5) == 0) gfx.fill(x + i, y + h - 2, x + i + 1, y + h - 1, darken);
        }
        for (int i = 0; i < h; i++) {
            if (rnd.nextInt(4) == 0) gfx.fill(x, y + i, x + 1, y + i + 1, 0x00000000);
            if (rnd.nextInt(4) == 0) gfx.fill(x + w - 1, y + i, x + w, y + i + 1, 0x00000000);
            if (rnd.nextInt(5) == 0) gfx.fill(x + 1, y + i, x + 2, y + i + 1, darken);
            if (rnd.nextInt(5) == 0) gfx.fill(x + w - 2, y + i, x + w - 1, y + i + 1, darken);
        }
    }

    public static void drawPin(GuiGraphics gfx, int cx, int cy, boolean brass) {
        int main  = brass ? PIN_BRASS : PIN_RED;
        int hi    = brass ? 0xFFE8C880 : 0xFFE84A4A;
        int lo    = brass ? 0xFF4A3820 : 0xFF400000;
        gfx.fill(cx - 5, cy - 3, cx + 6, cy + 7, 0x60000000);
        gfx.fill(cx - 4, cy - 4, cx + 5, cy + 5, main);
        gfx.fill(cx - 4, cy - 4, cx - 3, cy - 3, 0);
        gfx.fill(cx + 4, cy - 4, cx + 5, cy - 3, 0);
        gfx.fill(cx - 4, cy + 4, cx - 3, cy + 5, 0);
        gfx.fill(cx + 4, cy + 4, cx + 5, cy + 5, 0);
        gfx.fill(cx - 3, cy - 3, cx - 1, cy - 1, hi);
        gfx.fill(cx - 2, cy - 2, cx - 1, cy - 1, 0xFFFFFFFF);
        gfx.fill(cx + 2, cy + 2, cx + 4, cy + 4, lo);
    }

    public static void drawTape(GuiGraphics gfx, int x, int y, int w, int h, int alpha) {
        int body = (alpha << 24) | (TAPE & 0x00FFFFFF);
        int shadow = 0x40000000;
        gfx.fill(x, y, x + w, y + h, body);
        gfx.fill(x, y, x + w, y + 1, shadow);
        gfx.fill(x, y + h - 1, x + w, y + h, shadow);
        int dash = 0x60806030;
        for (int i = 0; i < h; i++) {
            if ((i & 1) == 0) {
                gfx.fill(x, y + i, x + 1, y + i + 1, dash);
                gfx.fill(x + w - 1, y + i, x + w, y + i + 1, dash);
            }
        }
    }

    public static void drawRectStamp(GuiGraphics gfx, Font font, String text, int cx, int cy, int color) {
        int w = font.width(text) + 14;
        int h = 16;
        int x = cx - w / 2;
        int y = cy - h / 2;
        drawRectStampAt(gfx, font, text, x, y, w, h, color);
    }

    public static void drawRectStampAt(GuiGraphics gfx, Font font, String text, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + 1, color);
        gfx.fill(x, y + h - 1, x + w, y + h, color);
        gfx.fill(x, y, x + 1, y + h, color);
        gfx.fill(x + w - 1, y, x + w, y + h, color);
        gfx.fill(x + 2, y + 2, x + w - 2, y + 3, color);
        gfx.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, color);
        gfx.fill(x + 2, y + 2, x + 3, y + h - 2, color);
        gfx.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, color);
        gfx.drawString(font, text, x + w / 2 - font.width(text) / 2, y + 5, color, false);
    }

    public static void drawStamp(GuiGraphics gfx, Font font, String text, int cx, int cy, int color) {
        drawRectStamp(gfx, font, text, cx, cy, color);
    }

    public static void drawRoundStamp(GuiGraphics gfx, Font font, int cx, int cy, int radius,
                                       String top, String bottom, int color) {
        int color2 = withAlpha(color, 0.4f);
        drawCircleOutline(gfx, cx, cy, radius, color);
        drawCircleOutline(gfx, cx, cy, radius - 3, color2);
        gfx.drawString(font, top, cx - font.width(top) / 2, cy - 6, color, false);
        int dashW = Math.min(radius - 6, 26);
        gfx.fill(cx - dashW / 2, cy + 3, cx + dashW / 2, cy + 4, withAlpha(color, 0.6f));
        gfx.drawString(font, bottom, cx - font.width(bottom) / 2, cy + 6, color, false);
    }

    private static void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int r, int color) {
        int x = r, y = 0, err = 0;
        while (x >= y) {
            plot(gfx, cx + x, cy + y, color);
            plot(gfx, cx + y, cy + x, color);
            plot(gfx, cx - y, cy + x, color);
            plot(gfx, cx - x, cy + y, color);
            plot(gfx, cx - x, cy - y, color);
            plot(gfx, cx - y, cy - x, color);
            plot(gfx, cx + y, cy - x, color);
            plot(gfx, cx + x, cy - y, color);
            y++;
            if (err <= 0) err += 2 * y + 1;
            if (err > 0) { x--; err -= 2 * x + 1; }
        }
    }

    private static void plot(GuiGraphics gfx, int x, int y, int color) {
        gfx.fill(x, y, x + 1, y + 1, color);
    }

    public static void drawHatchedBar(GuiGraphics gfx, int x, int y, int w, int h, float pct, int color) {
        gfx.fill(x, y, x + w, y + 1, INK);
        gfx.fill(x, y + h - 1, x + w, y + h, INK);
        gfx.fill(x, y, x + 1, y + h, INK);
        gfx.fill(x + w - 1, y, x + w, y + h, INK);
        int count = Math.max(1, (w - 6) / 4);
        int filled = (int) (count * Math.max(0f, Math.min(1f, pct)));
        int sc = (color & 0x00FFFFFF) | 0xE0000000;
        for (int i = 0; i < filled; i++) {
            int sx = x + 3 + i * 4;
            for (int k = 0; k < h - 4; k++) {
                int px = sx + k / 2;
                if (px >= x + w - 2) break;
                gfx.fill(px, y + 1 + k, px + 1, y + 2 + k, sc);
            }
        }
    }

    public static void drawHandDivider(GuiGraphics gfx, int x, int y, int w, int color) {
        for (int i = 0; i < w - 1; i++) {
            double t = i / (double) (w - 1);
            int dy = (int) Math.round(Math.sin(t * Math.PI * 2.2) * 1.2);
            gfx.fill(x + i, y + dy, x + i + 1, y + dy + 1, color);
            if (i % 9 == 0) gfx.fill(x + i, y + dy + 1, x + i + 1, y + dy + 2, withAlpha(color, 0.4f));
        }
    }

    public static void drawInkText(GuiGraphics gfx, Font font, Component text, int x, int y, int color) {
        gfx.drawString(font, text, x + 1, y + 1, 0x30000000, false);
        gfx.drawString(font, text, x, y, color, false);
    }

    public static void drawInkText(GuiGraphics gfx, Font font, String text, int x, int y, int color) {
        gfx.drawString(font, text, x + 1, y + 1, 0x30000000, false);
        gfx.drawString(font, text, x, y, color, false);
    }

    public static void drawScribble(GuiGraphics gfx, Font font, String text, int x, int y, int color) {
        gfx.drawString(font, text, x + 1, y + 1, 0x40000000, false);
        gfx.drawString(font, text, x - 1, y, withAlpha(color, 0.5f), false);
        gfx.drawString(font, text, x, y, color, false);
    }

    public static void drawInkStroke(GuiGraphics gfx, int x, int y, int w, int h, int color, long seed) {
        gfx.fill(x, y, x + w, y + h, color);
        Random rnd = new Random(seed);
        int dim = (color & 0x00FFFFFF) | 0x80000000;
        for (int i = 0; i < w; i++) {
            if (rnd.nextInt(6) == 0) gfx.fill(x + i, y, x + i + 1, y + h, dim);
        }
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (rr << 16) | (rg << 8) | rb;
    }

    public static int mul(int argb, float k) {
        k = Math.max(0f, Math.min(1.5f, k));
        int a = (argb >> 24) & 0xFF;
        int r = clamp((int) (((argb >> 16) & 0xFF) * k));
        int g = clamp((int) (((argb >> 8) & 0xFF) * k));
        int b = clamp((int) ((argb & 0xFF) * k));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int argb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public static float easeOut(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }

    public static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) return mc.level.getGameTime();
        return System.currentTimeMillis() / 50L;
    }

    /** Единый «звук перелистывания» для всех paper-виджетов: кнопок, чек-боксов,
     *  слайдеров, сегмент-переключателей. Заменяет дефолтный UI-клик. */
    public static void playPageFlip(net.minecraft.client.sounds.SoundManager handler) {
        try {
            handler.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    com.otbor.OtborSounds.PAGE_FLIP.get(), 1.0f, 0.7f));
        } catch (Throwable ignored) {}
    }

}
