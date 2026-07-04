package com.infection.client.gui;

import com.infection.client.ClientInfectionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Бумажный «листок» с самочувствием с пошаговой прорисовкой при открытии экрана.
 *
 * Состоит из ДВУХ секций:
 *   1. Авто-самочувствие по 5%-бакетам (noteText, обновляется сервером).
 *   2. Кастомный текст от админа (customNoteText) — отдельная секция ниже.
 *
 * Анимация (длительность ~280 мс): paper-fade → border perimeter trace →
 * заголовок-typewriter → подзаголовок-typewriter → линия-разделитель → авто-text
 * letter-by-letter → custom-text letter-by-letter.
 */
public final class InfectionNoteCard {

    public static final int CARD_W = 200;
    public static final int CARD_H_MIN = 100;

    private static final long DRAW_IN_MS = 280L;

    // Палитра (paper-стилистика, совпадает с PaperRender отбора).
    private static final int PAPER_LIGHT = 0xFFE9DCB9;
    private static final int PAPER_EDGE  = 0xFF8B7355;
    private static final int INK         = 0xFF2A1810;
    private static final int INK_FADED   = 0xFF6B5842;
    private static final int INK_RED     = 0xFF7A1F1F;
    private static final int PIN_RED     = 0xFF8B1A1A;

    private static final int PAD_X = 8;
    private static final int LINE_H = 10;

    /** Tracker: момент открытия текущего экрана — для анимации с нуля при каждом открытии инвентаря. */
    private static volatile Object lastScreen = null;
    private static volatile long lastOpenedAt = 0L;

    private InfectionNoteCard() {}

    /** Считает высоту карточки под текущий текст (для позиционирования). */
    public static int computeHeight() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return CARD_H_MIN;
        Font font = mc.font;

        String autoRaw = ClientInfectionCache.getNoteText(mc.player.getUUID());
        if (autoRaw == null || autoRaw.isEmpty()) {
            autoRaw = "Самочувствие хорошее.\nБольше нечего сказать...";
        }
        String customRaw = ClientInfectionCache.getCustomNoteText(mc.player.getUUID());

        int textW = CARD_W - PAD_X * 2;
        List<String> autoLines = wrap(font, autoRaw, textW);
        int autoH = autoLines.size() * LINE_H;

        int total = 30 /* header */ + autoH + 12 /* footer space */;
        if (customRaw != null && !customRaw.isEmpty()) {
            List<String> customLines = wrap(font, customRaw, textW);
            total += 8 /* divider gap */ + customLines.size() * LINE_H + 6;
        }
        return Math.max(CARD_H_MIN, total);
    }

    /**
     * Рисует записку с анимацией прорисовки. Возвращает фактическую высоту карточки.
     */
    public static int draw(GuiGraphics gfx, int cardX, int cardY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return 0;
        Font font = mc.font;

        // Триггерим анимацию каждый раз, когда экран меняется (открыли инвентарь — экран != предыдущему).
        Object curScreen = mc.screen;
        if (curScreen != lastScreen) {
            lastScreen = curScreen;
            lastOpenedAt = System.currentTimeMillis();
        }
        long age = System.currentTimeMillis() - lastOpenedAt;
        float t = clamp01(age / (float) DRAW_IN_MS);

        java.util.UUID id = mc.player.getUUID();

        String autoRaw = ClientInfectionCache.getNoteText(id);
        if (autoRaw == null || autoRaw.isEmpty()) {
            autoRaw = "Самочувствие хорошее.\nБольше нечего сказать...";
        }
        String customRaw = ClientInfectionCache.getCustomNoteText(id);
        boolean hasCustom = customRaw != null && !customRaw.isEmpty();

        int textW = CARD_W - PAD_X * 2;
        List<String> autoLines = wrap(font, autoRaw, textW);
        List<String> customLines = hasCustom ? wrap(font, customRaw, textW) : List.of();

        int cardH = computeHeight();

        // === 1. Бумага: alpha fade-in (0..0.18) ===
        float paperAlpha = phase(t, 0f, 0.18f);
        if (paperAlpha < 0.05f) return cardH;

        if (paperAlpha >= 0.99f) {
            gfx.fill(cardX + 2, cardY + 3, cardX + CARD_W + 2, cardY + cardH + 3, 0x60000000);
            gfx.fill(cardX, cardY, cardX + CARD_W, cardY + cardH, PAPER_LIGHT);
        } else {
            int a = (int) (paperAlpha * 255);
            gfx.fill(cardX + 2, cardY + 3, cardX + CARD_W + 2, cardY + cardH + 3, ((int)(0x60 * paperAlpha) << 24));
            int paper = (a << 24) | (PAPER_LIGHT & 0x00FFFFFF);
            gfx.fill(cardX, cardY, cardX + CARD_W, cardY + cardH, paper);
        }

        // === 2. Перо обводит лист по периметру (0.10..0.42) ===
        float borderTrace = phase(t, 0.10f, 0.42f);
        if (borderTrace > 0f) {
            tracePerimeter(gfx, cardX, cardY, CARD_W, cardH, borderTrace, PAPER_EDGE);
        }

        // === 3. Булавка (0.18..0.30) ===
        float pinAlpha = phase(t, 0.18f, 0.30f);
        if (pinAlpha > 0f) {
            drawPin(gfx, cardX + 8, cardY + 6, pinAlpha);
        }

        // === 4. Заголовок «ЗАПИСКА» — typewriter (0.22..0.42) ===
        float headerProg = phase(t, 0.22f, 0.42f);
        if (headerProg > 0f) {
            typewriter(gfx, font, "ЗАПИСКА", cardX + 22, cardY + 4, INK_RED, headerProg);
        }

        // === 5. Подзаголовок — typewriter (0.34..0.54) ===
        float subProg = phase(t, 0.34f, 0.54f);
        if (subProg > 0f) {
            typewriter(gfx, font, "ЛИЧНЫЙ ДНЕВНИК", cardX + 22, cardY + 14, INK_FADED, subProg);
        }

        // === 6. Линия-разделитель — выезжает слева (0.40..0.58) ===
        float divProg = phase(t, 0.40f, 0.58f);
        if (divProg > 0f) {
            int divW = (int) ((CARD_W - 16) * divProg);
            gfx.fill(cardX + 8, cardY + 24, cardX + 8 + divW, cardY + 25,
                    argbWithAlpha(INK_RED, 0.6f));
        }

        // === 7. Авто-текст: каждая строка появляется alpha-fade с лёгкой задержкой (0.48..0.78) ===
        // Без typewriter в теле — иначе пользователь видит «обрезанный» текст с курсором.
        int textY = cardY + 30;
        float autoStart = 0.48f;
        float autoEnd = 0.78f;
        float perLine = autoLines.isEmpty() ? 0 : (autoEnd - autoStart) / autoLines.size();
        for (int i = 0; i < autoLines.size(); i++) {
            String line = autoLines.get(i);
            if (textY + LINE_H > cardY + cardH - 6) break;
            float lineStart = autoStart + i * perLine;
            float lineProg = phase(t, lineStart, lineStart + perLine + 0.06f);
            if (lineProg > 0f) {
                int color = argbWithAlpha(INK, lineProg);
                gfx.drawString(font, line, cardX + PAD_X, textY, color, false);
            }
            textY += LINE_H;
        }

        // === 8. Кастомный текст (от админа): волнистый разделитель + alpha-fade (0.74..1.00) ===
        if (hasCustom && t > 0.70f) {
            int divY = textY + 4;
            float wDivProg = phase(t, 0.70f, 0.82f);
            int wDivW = (int) ((CARD_W - 24) * wDivProg);
            for (int i = 0; i < wDivW; i++) {
                int dy = (int) Math.round(Math.sin(i * 0.5) * 1.0);
                gfx.fill(cardX + 12 + i, divY + dy, cardX + 13 + i, divY + 1 + dy,
                        argbWithAlpha(INK_FADED, 0.55f));
            }
            textY += 10;

            float custStart = 0.78f;
            float custEnd = 1.00f;
            float perCust = customLines.isEmpty() ? 0 : (custEnd - custStart) / customLines.size();
            for (int i = 0; i < customLines.size(); i++) {
                String line = customLines.get(i);
                if (textY + LINE_H > cardY + cardH - 6) break;
                float lineStart = custStart + i * perCust;
                float lineProg = phase(t, lineStart, lineStart + perCust + 0.05f);
                if (lineProg > 0f) {
                    int color = argbWithAlpha(INK, lineProg);
                    gfx.drawString(font, line, cardX + PAD_X, textY, color, false);
                }
                textY += LINE_H;
            }
        }

        return cardH;
    }

    // ============================== ПРИМИТИВЫ АНИМАЦИИ ==============================

    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }

    private static float phase(float t, float start, float end) {
        if (end <= start) return t >= end ? 1f : 0f;
        return clamp01((t - start) / (end - start));
    }

    /** Перо обводит прямоугольник по периметру. */
    private static void tracePerimeter(GuiGraphics gfx, int x, int y, int w, int h,
                                       float progress, int color) {
        int total = (w + h) * 2;
        int len = (int) (total * progress);
        int rem = len;
        // top
        int seg = Math.min(rem, w);
        if (seg > 0) gfx.fill(x, y, x + seg, y + 1, color);
        rem -= w;
        // right
        if (rem > 0) {
            seg = Math.min(rem, h);
            gfx.fill(x + w - 1, y, x + w, y + seg, color);
            rem -= h;
        }
        // bottom
        if (rem > 0) {
            seg = Math.min(rem, w);
            gfx.fill(x + w - seg, y + h - 1, x + w, y + h, color);
            rem -= w;
        }
        // left
        if (rem > 0) {
            seg = Math.min(rem, h);
            gfx.fill(x, y + h - seg, x + 1, y + h, color);
        }
    }

    /** Текст печатается по буквам с курсором. */
    private static void typewriter(GuiGraphics gfx, Font font, String text,
                                   int x, int y, int color, float progress) {
        int n = text.length();
        int reveal = Math.min(n, (int) Math.ceil(n * progress));
        if (reveal <= 0) return;
        String shown = text.substring(0, reveal);
        gfx.drawString(font, shown, x, y, color, false);
        if (reveal < n) {
            int caretX = x + font.width(shown);
            gfx.fill(caretX, y - 1, caretX + 1, y + 8, argbWithAlpha(color, 0.7f));
        }
    }

    private static List<String> wrap(Font font, String raw, int maxW) {
        List<String> out = new ArrayList<>();
        for (String paragraph : raw.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                out.add("");
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String w : words) {
                String trial = cur.length() == 0 ? w : cur + " " + w;
                if (font.width(trial) > maxW && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(w);
                } else {
                    cur = new StringBuilder(trial);
                }
            }
            if (cur.length() > 0) out.add(cur.toString());
        }
        return out;
    }

    private static int argbWithAlpha(int argb, float alpha) {
        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static void drawPin(GuiGraphics gfx, int cx, int cy, float alpha) {
        int main  = argbWithAlpha(PIN_RED, alpha);
        int hi    = argbWithAlpha(0xFFE84A4A, alpha);
        int white = argbWithAlpha(0xFFFFFFFF, alpha);
        int lo    = argbWithAlpha(0xFF400000, alpha);
        int sh    = argbWithAlpha(0xFF000000, 0.4f * alpha);
        gfx.fill(cx - 5, cy - 3, cx + 6, cy + 7, sh);
        gfx.fill(cx - 4, cy - 4, cx + 5, cy + 5, main);
        gfx.fill(cx - 4, cy - 4, cx - 3, cy - 3, 0);
        gfx.fill(cx + 4, cy - 4, cx + 5, cy - 3, 0);
        gfx.fill(cx - 4, cy + 4, cx - 3, cy + 5, 0);
        gfx.fill(cx + 4, cy + 4, cx + 5, cy + 5, 0);
        gfx.fill(cx - 3, cy - 3, cx - 1, cy - 1, hi);
        gfx.fill(cx - 2, cy - 2, cx - 1, cy - 1, white);
        gfx.fill(cx + 2, cy + 2, cx + 4, cy + 4, lo);
    }
}
