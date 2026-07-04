package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Кастомный экран загрузки мира в стиле бумажной документации О.Т.Б.О.Р.
 * Прогресс-бар выполнен в виде штриховки (hatched bar), добавлены
 * декоративные элементы: скотч, булавки, штампы, чернильные кляксы,
 * «зачёркнутые» старые данные, печать отдела, «дырка от булавки».
 */
public class OtborGameLoadingScreen extends Screen {

    private long startTime = -1L;
    private float progress = 0f;
    private String currentStage = "ИНИЦИАЛИЗАЦИЯ";
    private String subStage = "загрузка модулей...";

    // Анимационные переменные
    private float scanlineOffset = 0f;
    private float glitchIntensity = 0f;
    private int dotCounter = 0;

    // Статистика
    private float fps = 0f;
    private long lastFpsTime = 0L;
    private int fpsCounter = 0;

    // Эффекты
    private final Random random = new Random();
    private final String[] tips = {
            "О.Т.Б.О.Р — Отдел Технического Безопасного Обеспечения Работы",
            "Лабиринт живёт своей жизнью",
            "Стены могут двигаться",
            "Ты не первый. Ты не последний.",
            "Доверяй только документам",
            "Каждый гривер когда-то был человеком",
            "Некоторые двери лучше не открывать",
            "Если слышишь шаги за спиной — не оборачивайся",
            "Бегущий не имеет имени",
            "Архивная запись №47: доверия нет"
    };
    private String currentTip = tips[0];
    private long tipChangeTime = 0L;

    // Для анимации штампа
    private float stampPhase = 0f;
    private boolean stampIncreasing = true;

    // Для чернильных клякс (фиксированные позиции)
    private final int[][] inkBlots = {
            {45, 120, 12, 8}, {320, 200, 18, 12}, {520, 340, 10, 14},
            {120, 380, 14, 10}, {600, 100, 8, 8}, {400, 450, 16, 10}
    };

    // Дырочка от булавки
    private int pinHoleX, pinHoleY;

    private static final int BAR_W = 400;
    private static final int BAR_H = 12;

    public OtborGameLoadingScreen() {
        super(Component.literal("ЗАГРУЗКА О.Т.Б.О.Р"));
    }

    @Override
    protected void init() {
        super.init();
        startTime = System.currentTimeMillis();
        tipChangeTime = startTime + 3000;
        pinHoleX = 30 + random.nextInt(100);
        pinHoleY = 30 + random.nextInt(80);

        // Симуляция загрузки (в реальности будет связано с реальным прогрессом)
        new Thread(() -> {
            try {
                simulateGameLoad();
            } catch (InterruptedException ignored) {}
        }).start();
    }

    private void simulateGameLoad() throws InterruptedException {
        String[] stages = {
                "ЗАГРУЗКА ЯДРА", "ИНИЦИАЛИЗАЦИЯ РЕСУРСОВ", "ПРОВЕРКА ЦЕЛОСТНОСТИ",
                "ЗАГРУЗКА ЗВУКОВ", "КОМПИЛЯЦИЯ ШЕЙДЕРОВ", "ПОДКЛЮЧЕНИЕ МОДУЛЕЙ",
                "ФИНАЛИЗАЦИЯ", "ЗАПУСК"
        };
        String[] subStages = {
                "загрузка системных файлов...", "подготовка ассетов...", "сканирование данных...",
                "инициализация аудиоподсистемы...", "компиляция графических эффектов...",
                "активация дополнительных модулей...", "финальная проверка...", "открытие портала..."
        };

        for (int i = 0; i <= 100; i++) {
            progress = i / 100f;
            int stageIndex = Math.min(stages.length - 1, i * stages.length / 100);
            currentStage = stages[stageIndex];
            subStage = subStages[stageIndex];
            Thread.sleep(20 + random.nextInt(15));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - startTime);
        float appear = Math.min(1f, elapsed / 500f);
        float easeAppear = PaperRender.easeOut(appear);

        // Обновление FPS
        updateFPS();

        // Обновление анимаций
        scanlineOffset = (scanlineOffset + 0.5f) % 12f;
        glitchIntensity = Math.max(0f, glitchIntensity - 0.01f);
        if (random.nextInt(300) == 0) glitchIntensity = 0.25f + random.nextFloat() * 0.4f;

        // Анимация штампа (пульсация)
        if (stampIncreasing) {
            stampPhase += 0.025f;
            if (stampPhase >= 1f) stampIncreasing = false;
        } else {
            stampPhase -= 0.015f;
            if (stampPhase <= 0.3f) stampIncreasing = true;
        }

        // Смена подсказок
        if (now >= tipChangeTime) {
            currentTip = tips[random.nextInt(tips.length)];
            tipChangeTime = now + 5000 + random.nextInt(3000);
        }

        dotCounter = (int) ((now / 400L) % 4);

        // Фоновая доска
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        int slide = (int) ((1f - easeAppear) * 30f);
        int paperW = Math.min(this.width - 100, 560);
        int paperH = Math.min(this.height - 100, 420);
        int paperX = (this.width - paperW) / 2;
        int paperY = (this.height - paperH) / 2 + slide;

        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW / 2f, paperY + paperH / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.5f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        // Бумажная карточка с эффектом глитча
        if (glitchIntensity > 0.05f && elapsed < 1500) {
            int offset = (int) (glitchIntensity * 5);
            PaperRender.drawPaper(gfx, offset, 0, paperW, paperH, easeAppear * 0.8f, PaperRender.PAPER_LIGHT);
            PaperRender.drawPaper(gfx, -offset, 0, paperW, paperH, easeAppear * 0.6f, PaperRender.PAPER_BASE);
        }
        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, easeAppear, PaperRender.PAPER_LIGHT);

        // Булавки
        PaperRender.drawPin(gfx, 16, 14, false);
        PaperRender.drawPin(gfx, paperW - 16, 14, true);

        // Дырочка от булавки (если булавка "выпала")
        if (elapsed > 800) {
            gfx.fill(paperX + pinHoleX, paperY + pinHoleY, paperX + pinHoleX + 3, paperY + pinHoleY + 3, 0xFF2A1A0E);
            gfx.fill(paperX + pinHoleX + 1, paperY + pinHoleY + 1, paperX + pinHoleX + 2, paperY + pinHoleY + 2, 0xFF000000);
        }

        // Скотч
        PaperRender.drawTape(gfx, 10, 8, 55, 12, 0xC0);
        PaperRender.drawTape(gfx, paperW - 65, 8, 55, 12, 0xC0);
        // Дополнительный скотч снизу для "надёжности"
        PaperRender.drawTape(gfx, paperW / 2 - 30, paperH - 14, 60, 10, 0x90);

        Font font = this.font;

        // Крикер (номер дела + метка)
        String kicker = "О.Т.Б.О.Р · СИСТЕМА ЗАГРУЗКИ · ДЕЛО №047 · УРОВЕНЬ 4";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, paperW / 2 - kw / 2, 10,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        // Заголовок с глитч-эффектом
        String title = "ЗАГРУЗКА";
        float titleScale = 2.8f;
        int titleW = (int) (font.width(title) * titleScale);
        int titleY = 28;
        float pulse = 0.5f + 0.5f * Mth.sin(elapsed * 0.003f);

        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - titleW / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        int glitch = (int) (50 + 50 * pulse);
        gfx.drawString(font, title, 1, 0, (glitch << 24) | 0x7A1F1F, false);
        gfx.drawString(font, title, -1, 0, (glitch << 24) | 0x226622, false);
        PaperRender.drawInkText(gfx, font, title, 0, 0, PaperRender.INK_DARK);
        gfx.pose().popPose();

        // Подзаголовок (маленькая каракуля)
        String subtitle = "ожидание ответа от узла...";
        gfx.drawString(font, subtitle, paperW / 2 - font.width(subtitle) / 2, titleY + 42,
                PaperRender.INK_SOFT, false);

        // Декоративная линия с эффектом "по руке"
        int lineY = titleY + 60;
        PaperRender.drawHandDivider(gfx, paperW / 2 - 160, lineY, 320,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));

        // === ПРОГРЕСС БАР (штрихованный, в стиле PaperRender.drawHatchedBar) ===
        int barX = paperW / 2 - BAR_W / 2;
        int barY = lineY + 32;

        // Рамка прогресс-бара (как вырезанное окно)
        gfx.fill(barX - 3, barY - 3, barX + BAR_W + 3, barY + BAR_H + 3, PaperRender.PAPER_EDGE);
        gfx.fill(barX - 2, barY - 2, barX + BAR_W + 2, barY + BAR_H + 2, PaperRender.PAPER_DARK);
        gfx.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, 0xFF1A0F08);
        gfx.fill(barX, barY, barX + BAR_W, barY + BAR_H, 0xFF2A1F15);

        // Заливка прогресса с хэтчингом (штриховкой)
        int filled = (int) (BAR_W * progress);
        for (int i = 0; i < filled; i++) {
            int color = PaperRender.withAlpha(PaperRender.INK_RED, 0.85f);
            // Создаём эффект штриховки: каждый 3-й пиксель ярче
            if ((i + (int) scanlineOffset) % 3 < 1) {
                color = PaperRender.withAlpha(PaperRender.INK_RED, 0.95f);
            }
            // Добавляем случайные "чернильные сгустки" в прогресс-баре
            if (random.nextInt(120) == 0 && i > 10) {
                gfx.fill(barX + i - 1, barY, barX + i + 2, barY + BAR_H,
                        PaperRender.withAlpha(PaperRender.INK_RED, 1.0f));
            }
            gfx.fill(barX + i, barY, barX + i + 1, barY + BAR_H, color);
        }

        // Процент с "миганием"
        String percent = String.format("%d%%", (int) (progress * 100));
        gfx.drawString(font, percent, paperW / 2 - font.width(percent) / 2, barY - 14,
                PaperRender.INK, false);

        // Текущий этап (крупно, как заголовок раздела)
        String stageText = currentStage;
        int stageW = font.width(stageText);
        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - stageW / 2f, barY + BAR_H + 8, 0);
        gfx.pose().scale(1.2f, 1.2f, 1f);
        gfx.drawString(font, stageText, 0, 0, PaperRender.INK_RED, false);
        gfx.pose().popPose();

        // Подэтап (мелкий шрифт)
        gfx.drawString(font, subStage, paperW / 2 - font.width(subStage) / 2, barY + BAR_H + 28,
                PaperRender.INK_FADED, false);

        // Точки загрузки (анимированные)
        String dots = ".".repeat(dotCounter);
        gfx.drawString(font, dots, paperW / 2 + stageW / 2 + 6, barY + BAR_H + 12,
                PaperRender.INK_RED, false);

        // === СТАТИСТИКА (три карточки с данными) ===
        int statsY = barY + BAR_H + 60;
        int colW = 130;
        int colGap = 15;

        // FPS
        drawStatBox(gfx, font, paperW / 2 - colW - colGap, statsY, colW, 48,
                "FPS", String.format("%.0f", fps), getFPSColor(), getFPSGraph(fps));

        // Прогресс (общий)
        drawStatBox(gfx, font, paperW / 2 - colW / 2, statsY, colW, 48,
                "ПРОГРЕСС", String.format("%.1f%%", progress * 100), PaperRender.INK, null);

        // Время загрузки
        long loadTime = elapsed / 1000;
        drawStatBox(gfx, font, paperW / 2 + colGap, statsY, colW, 48,
                "ВРЕМЯ", loadTime + "с", PaperRender.INK, null);

        // === ПОДСКАЗКА (совет) ===
        int tipY = statsY + 62;
        PaperRender.drawHandDivider(gfx, paperW / 2 - 170, tipY - 8, 340,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));

        String tipPrefix = ">";
        gfx.drawString(font, tipPrefix, paperW / 2 - 160, tipY,
                PaperRender.INK_RED, false);
        gfx.drawString(font, currentTip, paperW / 2 - 150, tipY,
                PaperRender.INK, false);

        // === ДЕКОРАТИВНЫЕ ЭЛЕМЕНТЫ ===

        // Чернильные кляксы (фиксированные позиции на бумаге)
        for (int[] blot : inkBlots) {
            if (elapsed > 500) {
                int alpha = (int) (0x44 * Math.min(1f, (elapsed - 500) / 1000f));
                gfx.fill(blot[0], blot[1], blot[0] + blot[2], blot[1] + blot[3],
                        (alpha << 24) | (PaperRender.INK_DARK & 0x00FFFFFF));
                // Некоторые кляксы с "хвостиком"
                if (blot[2] > 10) {
                    gfx.fill(blot[0] + blot[2] - 2, blot[1] + blot[3] - 3,
                            blot[0] + blot[2] + 3, blot[1] + blot[3] + 1,
                            (alpha << 24) | (PaperRender.INK_DARK & 0x00FFFFFF));
                }
            }
        }

        // Зачёркнутая старая запись (как будто поверх напечатали новый текст)
        if (elapsed > 1200) {
            gfx.pose().pushPose();
            gfx.pose().translate(paperW - 180, 110, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8f));
            gfx.drawString(font, "СТАРЫЙ МИР УДАЛЁН", 0, 0,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.4f), false);
            // Зачёркивание
            gfx.fill(0, 6, font.width("СТАРЫЙ МИР УДАЛЁН"), 8,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.4f));
            gfx.pose().popPose();
        }

        // === ШТАМП (пульсирующий, с эффектом "прихлопывания") ===
        float stampScale = 0.9f + 0.3f * stampPhase;
        int stampAlpha = (int) (180 + 75 * stampPhase);
        gfx.pose().pushPose();
        gfx.pose().translate(paperW - 65, paperH - 45, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-10f));
        gfx.pose().scale(stampScale, stampScale, 1f);
        PaperRender.drawRectStamp(gfx, font, "ЗАГРУЗКА", 0, 0,
                (stampAlpha << 24) | (PaperRender.INK_RED & 0x00FFFFFF));
        gfx.pose().popPose();

        // Дополнительная круглая печать "О.Т.Б.О.Р" в углу
        if (elapsed > 800) {
            gfx.pose().pushPose();
            gfx.pose().translate(25, paperH - 28, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-5f));
            int alpha = (int) (0xAA * Math.min(1f, (elapsed - 800) / 600f));
            PaperRender.drawRoundStamp(gfx, font, 0, 0, 24, "О.Т.Б.О.Р", "УЧТЕНО",
                    (alpha << 24) | (PaperRender.INK_RED & 0x00FFFFFF));
            gfx.pose().popPose();
        }

        // Версия и build
        String version = "OTBOR Core v2.4.7 · build 047";
        gfx.drawString(font, version, paperW - font.width(version) - 12, paperH - 16,
                PaperRender.INK_FADED, false);

        // Дополнительная метка "СОВ. СЕКРЕТНО" справа вверху
        gfx.pose().pushPose();
        gfx.pose().translate(paperW - 110, 18, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(5f));
        gfx.drawString(font, "СОВ. СЕКРЕТНО", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.7f), false);
        gfx.pose().popPose();

        gfx.pose().popPose();

        // Сканирующие линии (поверх всего, для атмосферы)
        drawScanlines(gfx, this.width, this.height, scanlineOffset);

        super.render(gfx, mouseX, mouseY, partialTick);

        // Переход на титульный экран после загрузки
        if (progress >= 1f && elapsed > 500) {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    minecraft.setOverlay(null);
                    minecraft.setScreen(new OtborTitleScreen());
                });
            }
        }
    }

    private void drawStatBox(GuiGraphics gfx, Font font, int x, int y, int w, int h,
                             String label, String value, int color, String graph) {
        // Рамка как у архивной карточки
        gfx.fill(x, y, x + w, y + h, 0x18000000);
        gfx.fill(x, y, x + w, y + 1, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));
        gfx.fill(x, y + h - 1, x + w, y + h, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));
        gfx.fill(x, y, x + 1, y + h, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));
        gfx.fill(x + w - 1, y, x + w, y + h, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));

        gfx.drawString(font, label, x + 6, y + 4, PaperRender.INK_FADED, false);

        float scale = 1.45f;
        int valW = (int) (font.width(value) * scale);
        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f - valW / 2f, y + 17, 0);
        gfx.pose().scale(scale, scale, 1f);
        gfx.drawString(font, value, 0, 0, color, false);
        gfx.pose().popPose();

        if (graph != null) {
            gfx.drawString(font, graph, x + 6, y + 34, PaperRender.INK_FADED, false);
        }
    }

    private void updateFPS() {
        fpsCounter++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            fps = fpsCounter * (1000f / (now - lastFpsTime));
            fpsCounter = 0;
            lastFpsTime = now;
        }
    }

    private int getFPSColor() {
        if (fps >= 60) return PaperRender.INK;
        if (fps >= 30) return PaperRender.INK_SOFT;
        return PaperRender.INK_RED;
    }

    private String getFPSGraph(float fpsValue) {
        int bars = Math.min(10, (int) (fpsValue / 8));
        return "█".repeat(bars) + "░".repeat(10 - bars);
    }

    private void drawScanlines(GuiGraphics gfx, int w, int h, float offset) {
        for (int y = (int) offset; y < h; y += 6) {
            gfx.fill(0, y, w, y + 1, 0x0A000000);
        }
    }

    @Override
    public void onClose() {
        // Запрещаем закрытие во время загрузки
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}