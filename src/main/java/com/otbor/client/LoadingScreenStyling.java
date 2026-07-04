package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

public final class LoadingScreenStyling {
    public static final int PAPER_MAX_W = 420;
    public static final int PAPER_MAX_H = 260;
    private static final Random random = new Random();

    // Чернильные кляксы (относительные координаты внутри бумаги)
    private static final int[][] INK_BLOTS = {
            {45, 88, 12, 8}, {320, 150, 18, 12}, {280, 210, 10, 14},
            {120, 230, 14, 10}, {350, 180, 8, 8}, {60, 200, 9, 7}
    };

    private static int pinHoleX = -1, pinHoleY = -1;

    private static float stampPhase = 0f;
    private static boolean stampIncreasing = true;
    private static long firstRenderTime = 0L;

    private static float cachedProgress = 0f;
    private static long lastProgressUpdate = 0L;

    // ===== АНИМАЦИЯ "БАРАБАН" для процентов =====
    private static float displayedProgress = 0f;   // то, что сейчас показывается
    private static long lastFrameTime = 0L;        // для расчёта dt
    private static Screen lastScreen = null;       // для сброса при смене экрана

    private LoadingScreenStyling() {}

    public static boolean isLoadingScreen(Screen screen) {
        return screen instanceof ReceivingLevelScreen
                || screen instanceof ConnectScreen
                || screen instanceof LevelLoadingScreen
                || screen instanceof ProgressScreen
                || screen instanceof GenericDirtMessageScreen;
    }

    public static int paperWidth(Screen screen)  { return Math.min(screen.width - 80, PAPER_MAX_W); }
    public static int paperHeight(Screen screen) { return Math.min(screen.height - 60, PAPER_MAX_H); }
    public static int paperX(Screen screen)      { return (screen.width - paperWidth(screen)) / 2; }
    public static int paperY(Screen screen)      { return (screen.height - paperHeight(screen)) / 2; }

    // ===================== РЕАЛЬНЫЙ ПРОГРЕСС =====================

    private static float getRealProgress(Screen screen) {
        long now = System.currentTimeMillis();

        try {
            if (screen instanceof LevelLoadingScreen loading) {
                // ★ ИСПРАВЛЕНИЕ: Получаем объект progressListener через рефлексию
                Field listenerField = LevelLoadingScreen.class.getDeclaredField("progressListener");
                listenerField.setAccessible(true);
                Object listener = listenerField.get(loading);

                if (listener != null) {
                    // Вызываем метод getProgress() у listener, который возвращает int (0..100)
                    Method getProgressMethod = listener.getClass().getMethod("getProgress");
                    int progressInt = (int) getProgressMethod.invoke(listener);

                    // Нормализуем в float (0.0 .. 1.0)
                    float val = Mth.clamp(progressInt, 0, 100) / 100f;
                    if (val >= 0f && val <= 1f) {
                        cachedProgress = val;
                        lastProgressUpdate = now;
                        return val;
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            if (screen instanceof ReceivingLevelScreen) {
                long elapsed = now - firstRenderTime;
                float simulated = Math.min(0.95f, elapsed / 8000f);
                cachedProgress = simulated;
                lastProgressUpdate = now;
                return simulated;
            }
        } catch (Exception ignored) {}

        if (now - lastProgressUpdate < 2000) {
            return cachedProgress;
        }
        return 0.5f;
    }

    // ===================== АНИМАЦИЯ ОТОБРАЖАЕМОГО ПРОГРЕССА =====================

    private static float animateDisplayedProgress(float realProgress, Screen screen) {
        long now = System.currentTimeMillis();

        if (screen != lastScreen) {
            displayedProgress = 0f;
            lastFrameTime = now;
            lastScreen = screen;
        }

        float dt = Math.min(0.1f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        float diff = realProgress - displayedProgress;

        float absDiff = Math.abs(diff);
        float speed;
        if (absDiff > 0.15f)      speed = 1.8f;
        else if (absDiff > 0.05f) speed = 0.8f;
        else if (absDiff > 0.01f) speed = 0.25f;
        else                      speed = 0.1f;

        if (absDiff < 0.003f) {
            displayedProgress = realProgress;
        } else {
            displayedProgress += Math.signum(diff) * speed * dt;
            if ((diff > 0 && displayedProgress > realProgress) ||
                    (diff < 0 && displayedProgress < realProgress)) {
                displayedProgress = realProgress;
            }
        }

        return displayedProgress;
    }

    // ===================== ТЕКСТ СТАТУСА =====================

    private static String getStatusText(Screen screen, float progress) {
        if (screen instanceof ReceivingLevelScreen) {
            if (progress < 0.3f) return "установка соединения...";
            if (progress < 0.7f) return "загрузка данных игрока...";
            return "финализация...";
        }
        if (screen instanceof LevelLoadingScreen) {
            if (progress < 0.2f) return "чтение региона...";
            if (progress < 0.5f) return "загрузка чанков...";
            if (progress < 0.8f) return "обработка сущностей...";
            return "почти готово...";
        }
        if (screen instanceof ConnectScreen) return "подключение к серверу...";
        return null;
    }

    // ===================== ОСНОВНОЙ РЕНДЕР =====================

    public static void drawPaperBackdrop(GuiGraphics gfx, Screen screen) {
        long now = System.currentTimeMillis();
        if (firstRenderTime == 0L) firstRenderTime = now;
        long elapsed = now - firstRenderTime;

        float realProgress = getRealProgress(screen);
        float progress = animateDisplayedProgress(realProgress, screen);

        int w = screen.width;
        int h = screen.height;

        // ★ ЗАМЕНА ЧЁРНОГО ФОНА НА ОБЩУЮ "ДОСКУ" (как в остальных меню) ★
        PaperRender.drawBoardBackground(gfx, w, h);

        int paperW = paperWidth(screen);
        int paperH = paperHeight(screen);
        int px = paperX(screen);
        int py = paperY(screen);

        if (pinHoleX == -1) {
            pinHoleX = 30 + random.nextInt(paperW - 60);
            pinHoleY = 30 + random.nextInt(paperH - 80);
        }

        if (stampIncreasing) {
            stampPhase += 0.02f;
            if (stampPhase >= 1f) stampIncreasing = false;
        } else {
            stampPhase -= 0.015f;
            if (stampPhase <= 0.3f) stampIncreasing = true;
        }

        PaperRender.drawPaper(gfx, px, py, paperW, paperH, 1.0f, PaperRender.PAPER_LIGHT);

        PaperRender.drawPin(gfx, px + 16, py + 12, false);
        PaperRender.drawPin(gfx, px + paperW - 16, py + 12, true);

        if (elapsed > 1000) {
            gfx.fill(px + pinHoleX, py + pinHoleY, px + pinHoleX + 3, py + pinHoleY + 3, 0xFF2A1A0E);
            gfx.fill(px + pinHoleX + 1, py + pinHoleY + 1, px + pinHoleX + 2, py + pinHoleY + 2, 0xFF000000);
        }

        PaperRender.drawTape(gfx, px + 12, py - 6, 58, 12, 0xC0);
        PaperRender.drawTape(gfx, px + paperW - 70, py - 6, 58, 12, 0xC0);
        PaperRender.drawTape(gfx, px + paperW / 2 - 35, py + paperH - 12, 70, 10, 0x90);

        Font font = Minecraft.getInstance().font;
        float pulse = 0.5f + 0.5f * Mth.sin(PaperRender.gameTime() * 0.07f);

        String kicker = "О.Т.Б.О.Р · ЗАГРУЗКА МИРА · ДЕЛО №047";
        int kickerW = font.width(kicker);
        gfx.drawString(font, kicker, px + paperW / 2 - kickerW / 2, py + 8,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        String title = titleFor(screen);
        float titleScale = 2.2f;
        int titleW = (int) (font.width(title) * titleScale);
        int titleY = py + 26;
        gfx.pose().pushPose();
        gfx.pose().translate(px + paperW / 2f - titleW / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        int glitch = (int) (55 + 45 * pulse);
        gfx.drawString(font, title, 1, 0, (glitch << 24) | 0x7A1F1F, false);
        gfx.drawString(font, title, -1, 0, (glitch << 24) | 0x226622, false);
        PaperRender.drawInkText(gfx, font, title, 0, 0, PaperRender.INK_DARK);
        gfx.pose().popPose();

        String sub = "Л А Б И Р И Н Т   ·   З А Г Р У З К А";
        int subW = font.width(sub);
        gfx.drawString(font, sub, px + paperW / 2 - subW / 2, titleY + (int) (10 * titleScale) + 2,
                PaperRender.INK_SOFT, false);

        int lineY = titleY + (int) (10 * titleScale) + 14;
        int lineW = paperW - 100;
        int lineColor = PaperRender.withAlpha(PaperRender.INK_RED, 0.55f + 0.45f * pulse);
        PaperRender.drawInkStroke(gfx, px + paperW / 2 - lineW / 2, lineY, lineW, 1, lineColor, 7L);

        // =====================================================================
        // ПРОГРЕСС-БАР РИСУЕТСЯ ТОЛЬКО НА ЭКРАНЕ ЗАГРУЗКИ МИРА (LevelLoadingScreen)
        // =====================================================================
        if (screen instanceof LevelLoadingScreen) {
            int barX = px + 50;
            int barY = lineY + 32;
            int barW = paperW - 100;
            int barH = 14;

            gfx.fill(barX - 3, barY - 3, barX + barW + 3, barY + barH + 3, PaperRender.PAPER_EDGE);
            gfx.fill(barX - 2, barY - 2, barX + barW + 2, barY + barH + 2, PaperRender.PAPER_DARK);
            gfx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF1A0F08);
            gfx.fill(barX, barY, barX + barW, barY + barH, 0xFF2A1F15);

            int filled = (int) (barW * progress);
            for (int i = 0; i < filled; i++) {
                int color = PaperRender.withAlpha(PaperRender.INK_RED, 0.85f);
                if ((i + (int) (PaperRender.gameTime() / 2)) % 3 < 1) {
                    color = PaperRender.withAlpha(PaperRender.INK_RED, 0.98f);
                }
                if (random.nextInt(180) == 0 && i > 5 && i < filled - 5) {
                    gfx.fill(barX + i - 1, barY, barX + i + 2, barY + barH,
                            PaperRender.withAlpha(PaperRender.INK_RED, 1.0f));
                }
                gfx.fill(barX + i, barY, barX + i + 1, barY + barH, color);
            }

            String percent;
            float remainDiff = Math.abs(realProgress - displayedProgress);
            if (remainDiff > 0.005f) {
                int basePct = (int) (progress * 100);
                int jitter = (int) ((PaperRender.gameTime() / 40) % 5) - 2;
                int shown = Mth.clamp(basePct + jitter, 0, 100);
                percent = String.format("%d%%", shown);
            } else {
                percent = String.format("%d%%", (int) (progress * 100));
            }

            gfx.drawString(font, percent, px + paperW / 2 - font.width(percent) / 2, barY - 14,
                    PaperRender.INK, false);

            String statusText = getStatusText(screen, progress);
            if (statusText != null && !statusText.isEmpty()) {
                gfx.drawString(font, statusText, px + paperW / 2 - font.width(statusText) / 2, barY + barH + 8,
                        PaperRender.INK_FADED, false);
            }

            int dotCount = (int) ((PaperRender.gameTime() / 250) % 4);
            String dots = ". ".repeat(dotCount);
            gfx.drawString(font, dots, px + paperW / 2 + 50, barY + barH + 7, PaperRender.INK_RED, false);
        }

        // === ДЕКОРАТИВНЫЕ ЭЛЕМЕНТЫ ===

        if (elapsed > 500) {
            int alpha = (int) (0x66 * Math.min(1f, (elapsed - 500) / 800f));
            for (int[] blot : INK_BLOTS) {
                gfx.fill(px + blot[0], py + blot[1], px + blot[0] + blot[2], py + blot[1] + blot[3],
                        (alpha << 24) | (PaperRender.INK_DARK & 0x00FFFFFF));
            }
        }

        if (elapsed > 1500) {
            gfx.pose().pushPose();
            gfx.pose().translate(px + paperW - 170, py + 140, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6f));
            gfx.drawString(font, "ПРЕДЫДУЩИЙ ВХОД", 0, 0,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.35f), false);
            gfx.fill(0, 6, font.width("ПРЕДЫДУЩИЙ ВХОД"), 8,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.35f));
            gfx.pose().popPose();
        }

        float stampScale = 0.9f + 0.25f * stampPhase;
        int stampAlpha = (int) (160 + 75 * stampPhase);
        gfx.pose().pushPose();
        gfx.pose().translate(px + paperW - 55, py + paperH - 38, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-9f));
        gfx.pose().scale(stampScale, stampScale, 1f);
        PaperRender.drawRectStamp(gfx, font, "ЗАГРУЗКА", 0, 0,
                (stampAlpha << 24) | (PaperRender.INK_RED & 0x00FFFFFF));
        gfx.pose().popPose();

        if (elapsed > 800) {
            gfx.pose().pushPose();
            gfx.pose().translate(px + 20, py + paperH - 26, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4f));
            int alpha = (int) (0xAA * Math.min(1f, (elapsed - 800) / 600f));
            PaperRender.drawRoundStamp(gfx, font, 0, 0, 22, "О.Т.Б.О.Р", "УЧТЕНО",
                    (alpha << 24) | (PaperRender.INK_RED & 0x00FFFFFF));
            gfx.pose().popPose();
        }

        drawTip(gfx, font, px, py, paperW, paperH, elapsed);

        String version = "OTBOR Core v2.4.7 · build 047";
        gfx.drawString(font, version, px + paperW - font.width(version) - 12, py + paperH - 14,
                PaperRender.INK_FADED, false);

        gfx.pose().pushPose();
        gfx.pose().translate(px + paperW - 100, py + 18, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(4f));
        gfx.drawString(font, "СОВ. СЕКРЕТНО", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.7f), false);
        gfx.pose().popPose();

        gfx.pose().pushPose();
        gfx.pose().translate(px + 15, py + paperH - 18, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-3f));
        gfx.drawString(font, "лист № " + ((firstRenderTime / 1000) % 999 + 100), 0, 0,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.5f));
        gfx.pose().popPose();
    }




    private static String titleFor(Screen screen) {
        if (screen instanceof ConnectScreen) return "ПОДКЛЮЧЕНИЕ";
        if (screen instanceof ReceivingLevelScreen) return "ВХОД В СТ-ЗОНУ";
        if (screen instanceof LevelLoadingScreen) return "ЗАГРУЗКА МИРА";
        if (screen instanceof ProgressScreen) return "СОХРАНЕНИЕ";
        return "ЗАГРУЗКА";
    }


    private static void drawTip(GuiGraphics gfx, Font font, int px, int py, int paperW, int paperH, long elapsed) {
        String[] tips = {
                "Стены могут двигаться",
                "Ты не первый. Ты не последний.",
                "Доверяй только документам",
                "Некоторые двери лучше не открывать",
                "О.Т.Б.О.Р наблюдает",
                "Бегущий не имеет имени",
                "Если слышишь шаги — не оборачивайся",
                "Гриверы когда-то были людьми"
        };
        int tipIndex = (int) ((elapsed / 4500) % tips.length);
        String tip = tips[tipIndex];

        int tipY = py + paperH - 52;
        PaperRender.drawHandDivider(gfx, px + 20, tipY - 6, paperW - 40,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.4f));

        gfx.drawString(font, "→", px + 22, tipY, PaperRender.INK_RED, false);
        gfx.drawString(font, tip, px + 36, tipY, PaperRender.INK_FADED, false);
    }

    private static void drawScanlines(GuiGraphics gfx, int w, int h) {
        for (int y = 0; y < h; y += 3) {
            gfx.fill(0, y, w, y + 1, 0x0C000000);
        }
    }

    private static void drawVignette(GuiGraphics gfx, int w, int h) {
        int topH = Math.min(50, h / 6);
        for (int i = 0; i < topH; i++) {
            int a = (int) (130 * (1.0 - i / (float) topH));
            gfx.fill(0, i, w, i + 1, (a << 24));
        }
        int botH = Math.min(80, h / 4);
        for (int i = 0; i < botH; i++) {
            int a = (int) (150 * (i / (float) botH));
            gfx.fill(0, h - botH + i, w, h - botH + i + 1, (a << 24));
        }
        int sideW = Math.min(50, w / 8);
        for (int i = 0; i < sideW; i++) {
            int a = (int) (80 * (1.0 - i / (float) sideW));
            gfx.fill(i, 0, i + 1, h, (a << 24));
            gfx.fill(w - i - 1, 0, w - i, h, (a << 24));
        }
    }
}