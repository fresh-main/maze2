package com.otbor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TheTruthScreen extends Screen {

    // ======= РАЗРАБОТЧИКИ =======
    // Данные из «Список.docx», фото из папки «Фитки», ужатые до 128×128 PNG в
    // assets/otbor/textures/dev_photos/.
    private static final String[] REAL_NAMES = {
            "Ness",
            "мирфа",
            "Тери",
            "MoSber",
            "R_DuRAcK",
            "Fresh__main",
            "Yan4ik",
            "Teljoran",
            "Older",
            "Relik",
            "AriyNex",
            "Ruguar",
            "Yonkof",
            "DENISPLAY36",
            "ТУРИСТИШЬ!"
    };
    private static final String[] REAL_ROLES = {
            "Начальник, автор задумки ивента, сценарист, планировщик",
            "Начальство, главный строитель, потратила 20к на киндеры",
            "Строитель, сценарист, автор идеи, моделлер, координация, лоровед",
            "Прораб, главный строитель, тот кто вёл всех к стенам",
            "Строитель, чертил чертежи и выставлял нулевой сектор и секции",
            "Мододел, создатель гриверов",
            "Строитель помощник-Джамшут, строил и обустраивал локации",
            "Просто строил лианы",
            "Строил лианы и кусты",
            "Строитель",
            "Билдер, тестировщик",
            "Украшал лабиринт, больницу и расставлял кусты",
            "Делал лабиринт, лианы, потом декорил",
            "Человек которому стало скучно — пошёл строить эту дичь",
            "Кодер (сделал интерфейс)"
    };
    private static final String[] REAL_COMMENTS = {
            "Уволил всех в разгар работы.",
            "Мне снилось как я строила лабиринт. У меня травма.",
            "Мы устали, помогите...",
            "Я задолбался, подайте на выздоровление зелёными!!!",
            "Я — Разумное Растение, инженер которое очень сильно устало",
            "Мой первый проект как мододела.",
            "Я не знал на что подписывался... (я рад). Согласен с Йонкофом на счёт ядерки",
            "Позвали строить лианы (против моей воли)",
            "Внёс свой малый вклад в создание проекта",
            "Пришёл чтобы осветили мой скин",
            "(Почти) всегда делал то что говорили начальники. Остался с (почти) целой психикой после стройки",
            "Дед",
            "Я делал то что говорили; после ивента мы запустим Хиросиму на этот лабиринт.",
            "Меня жёстко нагибали (строить). p.s. Жду ядерки после всего этого)",
            "Физик, купил клаву за 70к, кто я?"
    };
    private static final ResourceLocation[] REAL_PHOTOS = {
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/ness.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/mirfa.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/teri.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/mosber.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/r_durack.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/fresh__main.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/yan4ik.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/teljoran.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/older.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/relik.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/ariynex.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/ruguar.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/yonkof.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/denisplay36.png"),
            ResourceLocation.fromNamespaceAndPath("otbor", "textures/dev_photos/turistish.png")
    };
    /**
     * Индивидуальный «Вклад» каждого разработчика (1-5).
     * Ness — максимум (главный, сценарист, планировщик, уволил всех в разгар работы).
     */
    private static final int[] REAL_CONTRIB = {
            5, // Ness — начальник, автор задумки, сценарист
            5, // мирфа — начальство, главный строитель
            5, // Тери — автор идеи, моделлер, координация, лоровед
            5, // MoSber — прораб, главный строитель (поднят с 4)
            4, // R_DuRAcK — нулевой сектор
            5, // Fresh__main — мододел гриверов (поднят с 4)
            3, // Yan4ik — строитель помощник
            2, // Teljoran — лианы
            2, // Older — лианы и кусты
            2, // Relik — пришёл за светом
            3, // AriyNex — билдер, тестировщик
            2, // Ruguar — декор
            3, // Yonkof — лабиринт, лианы, декор
            2, // DENISPLAY36 — пришёл по нагибу
            5  // ТУРИСТИШЬ! — кодер, сделал интерфейс
    };
    private static final int REAL_DEV_COUNT = REAL_NAMES.length;
    private static final int DEV_COUNT = REAL_DEV_COUNT;

    private static final int CARD_W   = 200;
    private static final int CARD_H   = 160;
    private static final int COL_GAP  = 20;
    private static final int ROW_GAP  = 18;
    private static final int COLS     = 3;

    /** Длительность анимации одной карточки (миллисекунды от первого появления). */
    private static final long CARD_DRAW_MS = 900L;

    private final Screen parent;

    private double scrollY = 0.0;
    private int gridStartX;
    private int gridStartY;
    private int gridViewportTop;
    private int gridViewportBottom;

    /** Время последнего ВХОДА в зону видимости. Каждый раз, когда карточка
     *  переходит из «вне» в «в видимости», таймер сбрасывается и анимация рисуется заново. */
    private long[] cardEnterTime;
    /** Был ли в прошлом кадре в зоне видимости — для детекта переходов out→in. */
    private boolean[] cardWasInView;

    public TheTruthScreen(Screen parent) {
        super(Component.literal("УЗНАТЬ ПРАВДУ"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(PaperWidgets.paperButton(
                30, 30, 100, 22,
                Component.literal("<- НАЗАД"),
                b -> this.minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, null));

        int totalGridW = CARD_W * COLS + COL_GAP * (COLS - 1);
        gridStartX = this.width / 2 - totalGridW / 2;
        gridStartY = 154;

        gridViewportTop = 150;
        gridViewportBottom = this.height - 28;

        cardEnterTime = new long[DEV_COUNT];
        cardWasInView = new boolean[DEV_COUNT];
        Arrays.fill(cardEnterTime, -1L);
        // false по умолчанию — на первом кадре все станут «впервые в видимости»
        // и запустят анимацию.
        scrollY = 0.0;
    }

    private int totalGridHeight() {
        int rows = (DEV_COUNT + COLS - 1) / COLS;
        return rows * CARD_H + (rows - 1) * ROW_GAP;
    }

    private double maxScroll() {
        int viewportH = gridViewportBottom - gridViewportTop;
        return Math.max(0, totalGridHeight() - viewportH);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scrollY = Math.max(0.0, Math.min(maxScroll(), scrollY - delta * 36.0));
        return true;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        Font font = this.font;
        String kicker = "ФАЙЛ №02 · ДОСЬЕ · О.Т.Б.О.Р · УРОВЕНЬ 4";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, this.width / 2 - kw / 2, 34, 0xFFB8A581, false);

        String title = "УЗНАТЬ ПРАВДУ";
        float ts = 2.2f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f - tw / 2f, 44, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.PAPER_LIGHT, false);
        gfx.pose().popPose();

        renderPreamble(gfx);

        renderCardGrid(gfx, mouseX, mouseY);

        renderScrollbar(gfx);

        String quote = "ищи их. помни их. не доверяй.";
        float qs = 1.4f;
        int qw = (int) (font.width(quote) * qs);
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f - qw / 2f, this.height - 22, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-1.0f));
        gfx.pose().scale(qs, qs, 1f);
        gfx.drawString(font, quote, 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.85f), false);
        gfx.pose().popPose();

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderCardGrid(GuiGraphics gfx, int mouseX, int mouseY) {
        long now = System.currentTimeMillis();

        // Скрываем карточки, выходящие за пределы viewport-а.
        gfx.enableScissor(0, gridViewportTop, this.width, gridViewportBottom);

        for (int i = 0; i < DEV_COUNT; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = gridStartX + col * (CARD_W + COL_GAP);
            int y = gridStartY + row * (CARD_H + ROW_GAP) - (int) scrollY;

            // Считаем «в видимости» если карточка хоть на пиксель пересекает viewport.
            boolean inView = (y + CARD_H >= gridViewportTop - 10)
                          && (y <= gridViewportBottom + 10);

            if (!inView) {
                cardWasInView[i] = false;
                continue;
            }

            // Переход out → in: запускаем анимацию заново.
            if (!cardWasInView[i]) {
                cardEnterTime[i] = now;
                cardWasInView[i] = true;
            }

            float progress = Math.min(1f, (now - cardEnterTime[i]) / (float) CARD_DRAW_MS);

            renderCardDrawing(gfx, i, x, y, CARD_W, CARD_H, progress);
        }

        gfx.disableScissor();
    }

    /**
     * Рисует одну карточку с поэтапной анимацией «как будто рисуют».
     * Этапы (в долях прогресса):
     *   0.00 – 0.18  лист бумаги выезжает + проявляется
     *   0.18 – 0.32  рамка фотографии чертится по периметру
     *   0.32 – 0.42  силуэт «лица» проявляется
     *   0.42 – 0.62  строки текста печатаются по очереди
     *   0.62 – 0.78  пунктир-разделитель и заметка
     *   0.78 – 0.90  индикатор опасности заполняется по клеткам
     *   0.90 – 1.00  печать-штамп «приклеивается» с подскоком
     */
    private void renderCardDrawing(GuiGraphics gfx, int idx, int x, int y, int w, int h, float p) {
        Font font = this.font;
        boolean isReal = idx < REAL_DEV_COUNT;

        boolean lightPaper = (idx & 1) == 0;
        float baseRot = ((idx * 37) % 7) - 3f;
        int danger = isReal ? REAL_CONTRIB[idx] : (1 + Math.abs((idx * 17) % 5));

        // ----- Этап 1: появление листа -----
        float pAppear = clamp01(p / 0.18f);
        float ease = PaperRender.easeOut(pAppear);
        float drift = (1f - ease) * 26f * (((idx & 1) == 0) ? -1f : 1f);  // боковое скольжение
        float rotJiggle = baseRot + (1f - ease) * 10f;                    // лёгкий «бросок»
        float scale = 0.92f + 0.08f * ease;
        // Пока бумага не появилась, остальные слои не рисуем.
        if (pAppear < 0.05f) return;

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f + drift, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotJiggle));
        gfx.pose().scale(scale, scale, 1f);
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        int paperColor = lightPaper ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE;
        // drawPaperCard вместо drawPaper — без сотен seeded-spots и tornEdge,
        // ради FPS на сетке 100 карточек.
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, 0.85f + 0.15f * ease, paperColor);

        if (p >= 0.10f) {
            float pinPop = clamp01((p - 0.08f) / 0.12f);
            int pinY = (int) (4 - 6 * (1f - pinPop));
            PaperRender.drawPin(gfx, 12, 8 + pinY, lightPaper);
        }

        // ----- Этап 2: рамка фото чертится по периметру (обходим как контур) -----
        int photoX = 10;
        int photoY = 22;
        int photoW = 50;
        int photoH = 60;
        float pFrame = clamp01((p - 0.18f) / 0.14f);
        if (pFrame > 0f) {
            drawFrameStroke(gfx, photoX, photoY, photoW, photoH, pFrame);
        }

        // ----- Этап 3: фото / силуэт лица проявляется -----
        // Реальный разработчик: блитим PNG из assets/otbor/textures/dev_photos/.
        // Заглушка: «пиксель-арт» голова из ~9 fill (раньше был круг через
        // drawFilledCircle/Outline — ~256 fill в цикле, что убивало FPS).
        float pPhoto = clamp01((p - 0.32f) / 0.10f);
        if (pPhoto > 0f) {
            // фон фотографии (общий)
            gfx.fill(photoX + 1, photoY + 1, photoX + photoW - 1, photoY + photoH - 1,
                    PaperRender.withAlpha(PaperRender.PAPER_DARK, 0.4f * pPhoto));

            if (isReal) {
                // Блитим живое фото с альфа-fade через шейдерный цвет.
                // 128×128 source → photoW×(photoH-2) destination, чуть отступив от рамки.
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1f, 1f, 1f, pPhoto);
                gfx.blit(REAL_PHOTOS[idx],
                        photoX + 1, photoY + 1,
                        photoW - 2, photoH - 2,
                        0f, 0f, 128, 128, 128, 128);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                RenderSystem.disableBlend();
            } else {
                int headFill   = PaperRender.withAlpha(PaperRender.PAPER_BASE, pPhoto);
                int headBorder = PaperRender.withAlpha(PaperRender.INK, pPhoto);
                int hcx = photoX + photoW / 2;
                int hcy = photoY + 22;
                gfx.fill(hcx - 6, hcy - 7, hcx + 6, hcy + 7, headFill);
                gfx.fill(hcx - 7, hcy - 5, hcx - 6, hcy + 5, headFill);
                gfx.fill(hcx + 6, hcy - 5, hcx + 7, hcy + 5, headFill);
                gfx.fill(hcx - 6, hcy - 8, hcx + 6, hcy - 7, headBorder);
                gfx.fill(hcx - 6, hcy + 7, hcx + 6, hcy + 8, headBorder);
                gfx.fill(hcx - 8, hcy - 5, hcx - 7, hcy + 5, headBorder);
                gfx.fill(hcx + 7, hcy - 5, hcx + 8, hcy + 5, headBorder);
                gfx.fill(photoX + 6, photoY + 38, photoX + photoW - 6, photoY + 39, headBorder);
                gfx.fill(photoX + 8, photoY + 40, photoX + photoW - 8, photoY + 50, headFill);
                for (int px = photoX + 9; px < photoX + photoW - 9; px += 6) {
                    gfx.fill(px, photoY + 41, px + 2, photoY + 49, headBorder);
                }
            }

            // «крест» опасности — только на заглушках (опасные «архивные»)
            if (!isReal && danger >= 4 && pPhoto > 0.7f) {
                drawX(gfx, photoX + 2, photoY + 2, photoW - 4, photoH - 4,
                        PaperRender.withAlpha(PaperRender.INK_RED, 0.7f * pPhoto));
            }
        }

        // ----- Этап 4: тексты «печатаются» по строкам -----
        int tx = photoX + photoW + 8;
        int textW = w - tx - 8;
        // строка 1: ОБЪЕКТ № — появляется первой (всегда)
        if (p >= 0.42f) {
            float r = clamp01((p - 0.42f) / 0.05f);
            String obj = "ОБЪЕКТ №" + String.format("%03d", idx + 1);
            gfx.drawString(font, obj, tx, 22,
                    PaperRender.withAlpha(PaperRender.INK_FADED, r), false);
        }
        // строка 2: имя (большое)
        if (p >= 0.47f) {
            float r = clamp01((p - 0.47f) / 0.05f);
            float ns = 1.3f;
            String name = isReal ? REAL_NAMES[idx] : "БЕГУЩИЙ?";
            // ужимаем масштаб, если имя не вмещается
            float fitScale = ns;
            int nameW = (int) (font.width(name) * ns);
            if (nameW > textW) fitScale = ns * (textW / (float) nameW);
            gfx.pose().pushPose();
            gfx.pose().translate(tx, 32, 0);
            gfx.pose().scale(fitScale, fitScale, 1f);
            gfx.drawString(font, name, 0, 0, PaperRender.withAlpha(PaperRender.INK, r), false);
            gfx.pose().popPose();
        }
        // строка 3: код-метка / «кодовое имя» — красным
        if (p >= 0.52f) {
            float r = clamp01((p - 0.52f) / 0.05f);
            String code = isReal ? "\"" + ("ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(idx % 26)) + "-" + (idx + 1) + "\"" : "\"[? ? ?]\"";
            gfx.drawString(font, code, tx, 32 + (int) (9 * 1.3f) + 2,
                    PaperRender.withAlpha(PaperRender.INK_RED, r), false);
        }
        // строки 4-5: должность/роль — для реальных живой текст, для заглушек редакт-плашки
        int roleY1 = 32 + (int) (9 * 1.3f) + 14;
        int roleY2 = roleY1 + 10;
        if (isReal) {
            String[] wrapped = wrapTwoLines(font, REAL_ROLES[idx], textW);
            if (p >= 0.56f) {
                float r = clamp01((p - 0.56f) / 0.06f);
                gfx.drawString(font, wrapped[0], tx, roleY1,
                        PaperRender.withAlpha(PaperRender.INK_SOFT, r), false);
            }
            if (p >= 0.60f && !wrapped[1].isEmpty()) {
                float r = clamp01((p - 0.60f) / 0.06f);
                gfx.drawString(font, wrapped[1], tx, roleY2,
                        PaperRender.withAlpha(PaperRender.INK_SOFT, r), false);
            }
        } else {
            if (p >= 0.56f) {
                float r = clamp01((p - 0.56f) / 0.06f);
                drawRedactedBar(gfx, tx, roleY1, (int) (textW * 0.95f), 7, r);
            }
            if (p >= 0.60f) {
                float r = clamp01((p - 0.60f) / 0.06f);
                drawRedactedBar(gfx, tx, roleY2, (int) (textW * 0.7f), 7, r);
            }
        }

        // ----- Этап 5: пунктир-разделитель и «заметка» (комментарий разработчика) -----
        int noteY = Math.max(photoY + photoH + 6, 92);
        int dashY = noteY - 4;
        if (p >= 0.62f) {
            float r = clamp01((p - 0.62f) / 0.10f);
            int totalDashW = w - 20;
            int drawnW = (int) (totalDashW * r);
            for (int i = 0; i < drawnW; i += 4) {
                gfx.fill(10 + i, dashY, 10 + i + 2, dashY + 1, PaperRender.INK_FADED);
            }
        }
        if (isReal) {
            String[] wrapped = wrapTwoLines(font, REAL_COMMENTS[idx], w - 20);
            if (p >= 0.68f) {
                float r = clamp01((p - 0.68f) / 0.06f);
                gfx.drawString(font, wrapped[0], 10, noteY,
                        PaperRender.withAlpha(PaperRender.INK, r), false);
            }
            if (p >= 0.72f && !wrapped[1].isEmpty()) {
                float r = clamp01((p - 0.72f) / 0.06f);
                gfx.drawString(font, wrapped[1], 10, noteY + 11,
                        PaperRender.withAlpha(PaperRender.INK, r), false);
            }
        } else {
            if (p >= 0.68f) {
                float r = clamp01((p - 0.68f) / 0.06f);
                drawRedactedBar(gfx, 10, noteY, (int) ((w - 20) * 0.85f), 8, r);
            }
            if (p >= 0.72f) {
                float r = clamp01((p - 0.72f) / 0.06f);
                drawRedactedBar(gfx, 10, noteY + 11, (int) ((w - 20) * 0.55f), 8, r);
            }
        }

        // ----- Этап 6: индикатор опасности/вклада заполняется по клеткам -----
        if (p >= 0.78f) {
            float r = clamp01((p - 0.78f) / 0.12f);
            // у настоящих — «ВКЛАД» (уровень роли), у архивных заглушек — «ОПАСНОСТЬ»
            String prefix = isReal ? "ВКЛАД: " : "ОПАСНОСТЬ: ";
            int cellsLitTotal = (int) Math.ceil(5 * r);
            StringBuilder dg = new StringBuilder(prefix);
            for (int i = 0; i < 5; i++) {
                if (i < danger && i < cellsLitTotal) dg.append("■");
                else if (i < cellsLitTotal) dg.append("□");
                else dg.append(" ");
            }
            gfx.drawString(font, dg.toString(), w - font.width(dg.toString()) - 10, h - 14,
                    PaperRender.INK_RED, false);
        }

        // ----- Этап 7: штамп прихлопывается -----
        if (p >= 0.90f) {
            float r = clamp01((p - 0.90f) / 0.10f);
            // лёгкий «оверштут»: масштаб 1.6 → 1.0
            float stampScale = 1.6f - 0.6f * PaperRender.easeOut(r);
            int stampAlpha = (int) (255 * Math.min(1f, r * 1.5f));
            gfx.pose().pushPose();
            gfx.pose().translate(w - 38, h - 30, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12f));
            gfx.pose().scale(stampScale, stampScale, 1f);
            int stampColor = (stampAlpha << 24) | (PaperRender.INK_RED & 0x00FFFFFF);
            String stampLabel = isReal ? "АГЕНТ" : "ПРОВЕРЕНО";
            PaperRender.drawRectStamp(gfx, font, stampLabel, 0, 0, stampColor);
            gfx.pose().popPose();
        }

        gfx.pose().popPose();
    }

    /**
     * Чёрные «зацензуренные» полосы вместо текста — выглядит как засекреченный документ.
     * Появляются с прогрессом: левая часть полностью, правая ещё прозрачная.
     *
     * Раньше «потёртые усы» рисовались в цикле с per-bar Random — это давало ~20 fill
     * на полосу. Теперь 3 фиксированных штриха = 4 fill на полосу. Для FPS на 12 карточках
     * это разница в сотни fill-вызовов в кадр.
     */
    private void drawRedactedBar(GuiGraphics gfx, int x, int y, int w, int h, float progress) {
        int drawn = (int) (w * progress);
        if (drawn <= 0) return;
        gfx.fill(x, y, x + drawn, y + h, PaperRender.INK);
        if (drawn > 28) {
            int dim = PaperRender.withAlpha(PaperRender.INK, 0.5f);
            gfx.fill(x + drawn / 4,        y + h, x + drawn / 4 + 3, y + h + 1, dim);
            gfx.fill(x + drawn / 2 + 5,    y + h, x + drawn / 2 + 8, y + h + 1, dim);
            gfx.fill(x + 3 * drawn / 4,    y + h, x + 3 * drawn / 4 + 4, y + h + 1, dim);
        }
    }

    /**
     * Чертит рамку как будто пером по периметру: верх → правый бок → низ → левый бок.
     * Каждая сторона занимает четверть прогресса.
     */
    private void drawFrameStroke(GuiGraphics gfx, int x, int y, int w, int h, float progress) {
        int total = (w + h) * 2;
        int len = (int) (total * progress);
        int remaining = len;

        // верх (left → right)
        int seg = Math.min(remaining, w);
        if (seg > 0) gfx.fill(x, y, x + seg, y + 1, PaperRender.INK);
        remaining -= w;

        // правый бок (top → bottom)
        if (remaining > 0) {
            seg = Math.min(remaining, h);
            gfx.fill(x + w - 1, y, x + w, y + seg, PaperRender.INK);
            remaining -= h;
        }

        // низ (right → left)
        if (remaining > 0) {
            seg = Math.min(remaining, w);
            gfx.fill(x + w - seg, y + h - 1, x + w, y + h, PaperRender.INK);
            remaining -= w;
        }

        // левый бок (bottom → top)
        if (remaining > 0) {
            seg = Math.min(remaining, h);
            gfx.fill(x, y + h - seg, x + 1, y + h, PaperRender.INK);
        }
    }

    private void renderPreamble(GuiGraphics gfx) {
        Font font = this.font;
        int w = Math.min(440, this.width - 80);
        int h = 56;
        int x = this.width / 2 - w / 2;
        int y = 86;

        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-1.0f));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);
        PaperRender.drawPaper(gfx, 0, 0, w, h, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, w / 2, 4, false);

        gfx.drawString(font, "ПЕРЕХВАЧЕННЫЙ ФАЙЛ · ДАТА СТЁРТА", 14, 10,
                PaperRender.INK_FADED, false);

        gfx.drawString(font, "ты спрашивал КТО построил лабиринт.", 14, 22,
                PaperRender.INK, false);
        gfx.drawString(font, "вот список. запомни лица.", 14, 33,
                PaperRender.INK, false);

        String warn = "если встретишь -";
        String warn2 = "не верь ни одному слову.";
        gfx.drawString(font, warn, 14, 44, PaperRender.INK, false);
        gfx.drawString(font, warn2, 14 + font.width(warn) + 4, 44,
                PaperRender.INK_RED, true);

        gfx.pose().popPose();

        gfx.pose().pushPose();
        gfx.pose().translate(x + w - 30, y + h - 6, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4.0f));
        PaperRender.drawRectStamp(gfx, font, "СЕКРЕТНО", 0, 0, PaperRender.INK_RED);
        gfx.pose().popPose();
    }

    private void renderScrollbar(GuiGraphics gfx) {
        double max = maxScroll();
        if (max <= 0) return;
        int viewportH = gridViewportBottom - gridViewportTop;
        int trackX = this.width - 14;
        int trackY = gridViewportTop + 4;
        int trackH = viewportH - 8;
        gfx.fill(trackX, trackY, trackX + 2, trackY + trackH,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.4f));
        int thumbH = Math.max(20, (int) (trackH * (viewportH / (double) totalGridHeight())));
        int thumbY = trackY + (int) ((trackH - thumbH) * (scrollY / max));
        gfx.fill(trackX - 1, thumbY, trackX + 3, thumbY + thumbH, PaperRender.INK_RED);
    }

    // ============== примитивы ==============

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * Делит строку на две, чтобы каждая помещалась в {@code maxW} пикселей шрифта.
     * Перенос ищется по последнему пробелу. Если переносить не нужно — вторая строка
     * будет пустой. Если строка слишком длинная даже после переноса — вторая строка
     * обрезается с многоточием.
     */
    private static String[] wrapTwoLines(Font font, String text, int maxW) {
        if (text == null || text.isEmpty()) return new String[] { "", "" };
        if (font.width(text) <= maxW) return new String[] { text, "" };
        // ищем точку переноса по пробелу — берём максимально длинную первую строку
        int lo = 0, hi = text.length();
        int bestSp = -1;
        while (true) {
            int sp = text.indexOf(' ', lo);
            if (sp < 0) break;
            String head = text.substring(0, sp);
            if (font.width(head) <= maxW) {
                bestSp = sp;
                lo = sp + 1;
            } else {
                break;
            }
        }
        String first, second;
        if (bestSp > 0) {
            first  = text.substring(0, bestSp);
            second = text.substring(bestSp + 1);
        } else {
            // нет подходящего пробела — режем посимвольно
            int cut = 1;
            while (cut < text.length() && font.width(text.substring(0, cut + 1)) <= maxW) cut++;
            first  = text.substring(0, cut);
            second = text.substring(cut);
        }
        // вторая тоже подрезаем, если нужно
        if (font.width(second) > maxW) {
            int cut = second.length();
            while (cut > 1 && font.width(second.substring(0, cut) + "…") > maxW) cut--;
            second = second.substring(0, Math.max(1, cut)) + "…";
        }
        return new String[] { first, second };
    }

    private static void drawFilledCircle(GuiGraphics gfx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r * r)
                    gfx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
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

    private static void drawX(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        // Шаг 3 вместо 1 — пиксельный X выглядит так же, fill-вызовов втрое меньше.
        int n = Math.min(w, h);
        for (int i = 0; i < n; i += 3) {
            gfx.fill(x + i, y + i, x + i + 3, y + i + 3, color);
            gfx.fill(x + w - 1 - i, y + i, x + w + 2 - i, y + i + 3, color);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
