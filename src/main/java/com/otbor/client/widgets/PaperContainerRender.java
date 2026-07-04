package com.otbor.client.widgets;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.List;

/**
 * Стилизация любого контейнерного экрана (сундук, верстак, печь, варка, наковальня и т.д.)
 * под бумажную записку с пошаговой «прорисовкой» при открытии.
 *
 * Анимация (250 мс от момента {@code openedAtMs}):
 *   0–30 мс  — лист появляется (alpha fade)
 *   30–110 мс — рамка обводится по периметру, как будто пером
 *   60–140 мс — заголовок печатается по буквам (typewriter)
 *   100–230 мс — рамки слотов скетчатся волной слева-сверху → справа-снизу
 *   200–250 мс — штамп-печать «прихлопывается» с overshoot-skale
 *   80–250 мс — вспомогательные элементы (разделители, угловые рисунки) fade-in
 *
 * Доп. фишки:
 *   • ручные слот-рамки с лёгкими утолщениями в углах и каплями чернил
 *   • кастомные подписи для самых частых контейнеров (верстак, печь, варка, наковальня)
 *   • штампы и стрелки между логическими группами слотов
 *   • рукописная аннотация в правом поле — что куда класть
 */
public final class PaperContainerRender {

    private PaperContainerRender() {}

    /** Полная длительность вступительной анимации, миллисекунды. */
    public static final long DRAW_IN_MS = 250L;

    // === Глобальный tracker открытия экрана: один экран одномоментно, анимация общая ===
    private static Object currentScreen = null;
    private static long currentOpenedAt = 0L;

    /** Регистрирует факт открытия экрана. Если экран уже зарегистрирован — ничего не меняет. */
    public static long openedAtFor(Object screen) {
        if (screen == null) return System.currentTimeMillis();
        if (currentScreen != screen) {
            currentScreen = screen;
            currentOpenedAt = System.currentTimeMillis();
        }
        return currentOpenedAt;
    }

    /** Прогресс анимации (0..1) для экрана. Если время открытия не зарегистрировано — мгновенно 1.0. */
    public static float animProgress(Object screen) {
        long start = openedAtFor(screen);
        long age = System.currentTimeMillis() - start;
        return clamp01(age / (float) DRAW_IN_MS);
    }

    /** Главный entry-point. Рисует ВСЁ paper-оформление поверх ванильного `renderBg`. */
    public static void renderContainer(GuiGraphics gfx,
                                       AbstractContainerScreen<?> screen,
                                       int leftPos, int topPos,
                                       int imageWidth, int imageHeight,
                                       long openedAtMs) {
        long age = System.currentTimeMillis() - openedAtMs;
        float t = clamp01(age / (float) DRAW_IN_MS);

        Font font = Minecraft.getInstance().font;
        Layout layout = Layout.detect(screen);

        // === 1. Лист бумаги (с alpha по началу анимации) ===
        float paperAlpha = phase(t, 0.0f, 0.12f);
        renderPaperBase(gfx, leftPos, topPos, imageWidth, imageHeight, paperAlpha);

        if (paperAlpha < 0.05f) return;

        // === 2. Перо обводит лист по периметру ===
        float borderTrace = phase(t, 0.12f, 0.44f);
        if (borderTrace > 0f) {
            tracePerimeter(gfx, leftPos + 4, topPos + 4,
                    imageWidth - 8, imageHeight - 8,
                    borderTrace, PaperRender.INK, 1);
        }

        // === 3. Булавки в углах ===
        if (t > 0.2f) {
            PaperRender.drawPin(gfx, leftPos + 8, topPos + 8, false);
            PaperRender.drawPin(gfx, leftPos + imageWidth - 8, topPos + 8, true);
        }

        // === 4. Заголовок (typewriter) ===
        float headerProg = phase(t, 0.24f, 0.56f);
        if (headerProg > 0f) {
            int headerY = topPos + 6;
            String header = layout.header;
            int hw = font.width(header);
            int hx = leftPos + imageWidth / 2 - hw / 2;
            typewriter(gfx, font, header, hx, headerY, PaperRender.INK_RED, headerProg);

            // Кикер-надпись над заголовком — мелкая, выцветшая.
            if (t > 0.36f) {
                float kickProg = phase(t, 0.36f, 0.56f);
                String kicker = layout.kicker;
                int kw = font.width(kicker);
                gfx.drawString(font, kicker,
                        leftPos + imageWidth / 2 - kw / 2, topPos - 12,
                        PaperRender.withAlpha(PaperRender.INK_FADED, kickProg), false);
            }

            // Декоративная подчёркивающая «волна» под заголовком.
            float divProg = phase(t, 0.40f, 0.60f);
            if (divProg > 0f) {
                int divW = (int) ((imageWidth - 28) * divProg);
                PaperRender.drawHandDivider(gfx, leftPos + 14, topPos + 17, divW,
                        PaperRender.withAlpha(PaperRender.INK_FADED, 0.7f));
            }
        }

        // === 5. Группы слотов и подписи к ним ===
        // Сначала — секционные подписи (ставим до слот-рамок, чтобы рамки слотов перекрывали края подписи если нужно)
        for (Layout.Section sec : layout.sections) {
            float sp = phase(t, sec.startT, sec.startT + 0.18f);
            if (sp <= 0f) continue;
            int alpha = (int) (255 * sp);
            int col = (alpha << 24) | (PaperRender.INK_RED & 0xFFFFFF);
            int sx = leftPos + sec.x;
            int sy = topPos + sec.y;
            gfx.drawString(font, sec.label, sx, sy, col, false);
            // тонкая чёрточка под лейблом
            int lw = (int) (font.width(sec.label) * sp);
            gfx.fill(sx, sy + 9, sx + lw, sy + 10,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.5f * sp));
        }

        // === 6. Скетч-рамки слотов, волна сверху-слева вниз-вправо ===
        renderSlotSketches(gfx, screen.getMenu(), leftPos, topPos, imageWidth, imageHeight, t);

        // === 7. Стрелки между слот-группами (если есть) ===
        for (Layout.Arrow arr : layout.arrows) {
            float ap = phase(t, arr.startT, arr.startT + 0.12f);
            if (ap <= 0f) continue;
            drawSketchArrow(gfx,
                    leftPos + arr.x1, topPos + arr.y1,
                    leftPos + arr.x2, topPos + arr.y2,
                    ap, PaperRender.INK);
        }

        // === 8. Штампы (с overshoot-плюхой) ===
        for (Layout.Stamp stamp : layout.stamps) {
            float sp = phase(t, stamp.startT, stamp.startT + 0.10f);
            if (sp <= 0f) continue;
            float ease = easeBackOut(sp);
            float scale = 1.6f - 0.6f * ease;
            int alpha = (int) (255 * Math.min(1f, sp * 1.6f));
            int color = (alpha << 24) | (stamp.color & 0xFFFFFF);

            gfx.pose().pushPose();
            gfx.pose().translate(leftPos + stamp.cx, topPos + stamp.cy, 0);
            gfx.pose().mulPose(Axis.ZP.rotationDegrees(stamp.rotation));
            gfx.pose().scale(scale, scale, 1f);
            PaperRender.drawRectStamp(gfx, font, stamp.text, 0, 0, color);
            gfx.pose().popPose();
        }

        // === 9. Угловые рисунки и аннотации ===
        for (Layout.Doodle d : layout.doodles) {
            float dp = phase(t, d.startT, d.startT + 0.20f);
            if (dp <= 0f) continue;
            d.drawer.draw(gfx, leftPos + d.x, topPos + d.y, dp);
        }

        // === 10. Аннотация-стрелка с подписью (если есть) ===
        if (layout.annotation != null) {
            float ap = phase(t, 0.55f, 0.85f);
            if (ap > 0f) {
                Layout.Annotation ann = layout.annotation;
                int alpha = (int) (255 * ap);
                int col = (alpha << 24) | (PaperRender.INK_FADED & 0xFFFFFF);
                int ax = leftPos + ann.x;
                int ay = topPos + ann.y;
                gfx.drawString(font, ann.text, ax, ay, col, false);
                // курсивная стрелка от текста к мишени
                drawSketchArrow(gfx,
                        ax - 6, ay + 4,
                        leftPos + ann.targetX, topPos + ann.targetY,
                        ap, PaperRender.withAlpha(PaperRender.INK_FADED, 0.85f));
            }
        }
    }

    // ============================== ПЕРОВЫЕ ПРИМИТИВЫ ==============================

    private static void renderPaperBase(GuiGraphics gfx, int x, int y, int w, int h, float alpha) {
        if (alpha <= 0f) return;
        if (alpha >= 0.99f) {
            PaperRender.drawPaper(gfx, x, y, w, h, 1.0f, PaperRender.PAPER_LIGHT);
            return;
        }
        // Полу-прозрачный paper: рисуем по-простому fill чтобы не плодить случайные пятна,
        // потом заменим на полный drawPaper когда alpha = 1. Граничный случай — короткое окно
        // 30 мс, не критично.
        int a = (int) (alpha * 255);
        int paper = (a << 24) | (PaperRender.PAPER_LIGHT & 0xFFFFFF);
        gfx.fill(x, y, x + w, y + h, paper);
    }

    /** Перо обводит прямоугольник по периметру: top → right → bottom → left. */
    public static void tracePerimeter(GuiGraphics gfx, int x, int y, int w, int h,
                                      float progress, int color, int thickness) {
        int total = (w + h) * 2;
        int len = (int) (total * progress);
        int remaining = len;

        // top (left → right)
        int seg = Math.min(remaining, w);
        if (seg > 0) gfx.fill(x, y, x + seg, y + thickness, color);
        remaining -= w;

        // right (top → bottom)
        if (remaining > 0) {
            seg = Math.min(remaining, h);
            gfx.fill(x + w - thickness, y, x + w, y + seg, color);
            remaining -= h;
        }

        // bottom (right → left)
        if (remaining > 0) {
            seg = Math.min(remaining, w);
            gfx.fill(x + w - seg, y + h - thickness, x + w, y + h, color);
            remaining -= w;
        }

        // left (bottom → top)
        if (remaining > 0) {
            seg = Math.min(remaining, h);
            gfx.fill(x, y + h - seg, x + thickness, y + h, color);
        }

        // «капля чернил» на острие пера (только пока обводка идёт)
        if (progress < 1f && progress > 0.02f) {
            int[] tip = perimeterPoint(x, y, w, h, len, total);
            gfx.fill(tip[0] - 1, tip[1] - 1, tip[0] + 2, tip[1] + 2, color);
        }
    }

    private static int[] perimeterPoint(int x, int y, int w, int h, int dist, int total) {
        int d = Math.max(0, Math.min(total, dist));
        if (d <= w)            return new int[]{x + d, y};
        if (d <= w + h)        return new int[]{x + w, y + (d - w)};
        if (d <= 2 * w + h)    return new int[]{x + w - (d - w - h), y + h};
        return new int[]{x, y + h - (d - 2 * w - h)};
    }

    /** Текст печатается по буквам, с курсором «|» пока идёт. */
    public static void typewriter(GuiGraphics gfx, Font font, String text,
                                  int x, int y, int color, float progress) {
        int n = text.length();
        int reveal = Math.min(n, (int) Math.ceil(n * progress));
        if (reveal <= 0) return;
        String shown = text.substring(0, reveal);
        gfx.drawString(font, shown, x, y, color, false);
        if (reveal < n) {
            int caretX = x + font.width(shown);
            gfx.fill(caretX, y - 1, caretX + 1, y + 8,
                    PaperRender.withAlpha(color, 0.7f));
        }
    }

    /** Все ячейки `menu.slots`, чьи координаты валидны и слот активен. */
    private static void renderSlotSketches(GuiGraphics gfx, AbstractContainerMenu menu,
                                           int leftPos, int topPos, int imageWidth, int imageHeight,
                                           float t) {
        List<Slot> slots = menu.slots;
        // считаем общую «волну»: сортируем слоты по y, потом x — рисуем каскадом.
        // Слот k стартует в момент slotStart + k * spacing. Длительность 0.10 на каждый.
        float groupStart = 0.40f;
        float groupEnd = 0.92f;
        int visibleCount = 0;
        for (Slot s : slots) {
            if (!s.isActive()) continue;
            if (s.x < 0 || s.y < 0) continue;
            if (s.x > imageWidth + 200 || s.y > imageHeight + 200) continue; // спрятанные слоты
            visibleCount++;
        }
        if (visibleCount == 0) return;
        float spacing = Math.min(0.04f, (groupEnd - groupStart) / Math.max(1, visibleCount));
        float dur = 0.12f;

        int idx = 0;
        // отсортированный обход — по позиции на экране
        Slot[] sorted = slots.stream()
                .filter(s -> s.isActive() && s.x >= 0 && s.y >= 0
                        && s.x <= imageWidth + 200 && s.y <= imageHeight + 200)
                .sorted((a, b) -> {
                    int dy = Integer.compare(a.y, b.y);
                    return dy != 0 ? dy : Integer.compare(a.x, b.x);
                })
                .toArray(Slot[]::new);

        for (Slot slot : sorted) {
            float start = groupStart + idx * spacing;
            float local = phase(t, start, start + dur);
            sketchSlotBox(gfx, leftPos + slot.x, topPos + slot.y, local);
            idx++;
        }
    }

    /**
     * Рамка-«скетч» вокруг 16×16 слота. Перо обводит по периметру за 4 хода:
     * top → right → bottom → left, с лёгкими утолщениями в углах.
     */
    public static void sketchSlotBox(GuiGraphics gfx, int sx, int sy, float progress) {
        if (progress <= 0f) return;

        // Подложка-затемнение появляется сразу. Раньше alpha=0x55 (33%) — на светлой
        // бумаге пустые слоты рюкзака были еле различимы, игроки жаловались что у рюкзака
        // «нет своего инвентаря». Поднимаем до 0xA0 (~63%) — слот теперь чётко выделен
        // тёмным квадратом, видно где можно положить предмет.
        if (progress > 0.02f) {
            int a = (int) (0xA0 * Math.min(1f, progress * 4f));
            gfx.fill(sx, sy, sx + 16, sy + 16, (a << 24));
        }

        if (progress >= 1f) {
            // полная рамка — финальный вариант
            gfx.fill(sx - 1, sy - 1, sx + 17, sy,     PaperRender.INK);
            gfx.fill(sx - 1, sy + 16, sx + 17, sy + 17, PaperRender.INK);
            gfx.fill(sx - 1, sy - 1, sx,     sy + 17, PaperRender.INK);
            gfx.fill(sx + 16, sy - 1, sx + 17, sy + 17, PaperRender.INK);
            // лёгкая верхняя «искра» света — даёт глубину
            gfx.fill(sx, sy, sx + 16, sy + 1, 0x20FFFFFF);
            // капли чернил в углах — от руки
            gfx.fill(sx - 1, sy - 1, sx + 1, sy + 1, PaperRender.INK);
            gfx.fill(sx + 15, sy - 1, sx + 17, sy + 1, PaperRender.INK);
            gfx.fill(sx - 1, sy + 15, sx + 1, sy + 17, PaperRender.INK);
            gfx.fill(sx + 15, sy + 15, sx + 17, sy + 17, PaperRender.INK);
            return;
        }

        // 4 stage перимэтр: каждое ребро в свой квартал прогресса
        int color = PaperRender.INK;
        int side = 18; // вместе с -1/+17 рамкой
        int totalLen = side * 4;
        int drawn = (int) (totalLen * progress);
        int rem = drawn;

        // top: from (sx-1, sy-1) → (sx+17, sy-1)
        int seg = Math.min(rem, side);
        if (seg > 0) gfx.fill(sx - 1, sy - 1, sx - 1 + seg, sy, color);
        rem -= side;

        // right: (sx+17, sy-1) → (sx+17, sy+17)
        if (rem > 0) {
            seg = Math.min(rem, side);
            gfx.fill(sx + 16, sy - 1, sx + 17, sy - 1 + seg, color);
            rem -= side;
        }

        // bottom: (sx+17, sy+17) → (sx-1, sy+17)
        if (rem > 0) {
            seg = Math.min(rem, side);
            gfx.fill(sx + 17 - seg, sy + 16, sx + 17, sy + 17, color);
            rem -= side;
        }

        // left: (sx-1, sy+17) → (sx-1, sy-1)
        if (rem > 0) {
            seg = Math.min(rem, side);
            gfx.fill(sx - 1, sy + 17 - seg, sx, sy + 17, color);
        }
    }

    /** Маленькая стрелка от (x1,y1) к (x2,y2) пунктиром, с наконечником. */
    private static void drawSketchArrow(GuiGraphics gfx, int x1, int y1, int x2, int y2,
                                        float progress, int color) {
        if (progress <= 0f) return;
        int alpha = (int) (255 * Math.min(1f, progress));
        int col = (alpha << 24) | (color & 0xFFFFFF);
        // Пунктирная линия
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return;
        int reveal = (int) (steps * progress);
        for (int i = 0; i < reveal; i += 3) {
            int px = x1 + (int) (dx * (i / (float) steps));
            int py = y1 + (int) (dy * (i / (float) steps));
            gfx.fill(px, py, px + 1, py + 1, col);
        }
        // Наконечник в самом конце
        if (progress > 0.85f) {
            // упрощённый наконечник — три пиксельных штриха в направлении
            int hx = x2;
            int hy = y2;
            // определяем доминирующее направление
            if (Math.abs(dx) >= Math.abs(dy)) {
                int sgn = dx > 0 ? -1 : 1;
                gfx.fill(hx + sgn, hy - 1, hx + sgn + 1, hy, col);
                gfx.fill(hx + sgn * 2, hy - 2, hx + sgn * 2 + 1, hy - 1, col);
                gfx.fill(hx + sgn, hy + 1, hx + sgn + 1, hy + 2, col);
                gfx.fill(hx + sgn * 2, hy + 2, hx + sgn * 2 + 1, hy + 3, col);
            } else {
                int sgn = dy > 0 ? -1 : 1;
                gfx.fill(hx - 1, hy + sgn, hx, hy + sgn + 1, col);
                gfx.fill(hx - 2, hy + sgn * 2, hx - 1, hy + sgn * 2 + 1, col);
                gfx.fill(hx + 1, hy + sgn, hx + 2, hy + sgn + 1, col);
                gfx.fill(hx + 2, hy + sgn * 2, hx + 3, hy + sgn * 2 + 1, col);
            }
        }
    }

    // ============================== АНИМАЦИОННЫЕ ХЕЛПЕРЫ ==============================

    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }

    /** Линейный «фазер»: 0 до t==start, 1 после t==end, линейный переход между. */
    public static float phase(float t, float start, float end) {
        if (end <= start) return t >= end ? 1f : 0f;
        return clamp01((t - start) / (end - start));
    }

    /** Overshoot-easing для штампа — «прихлоп» с лёгким перебросом. */
    public static float easeBackOut(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    // ============================== LAYOUT ПО ТИПАМ КОНТЕЙНЕРОВ ==============================

    /**
     * Конкретная композиция элементов под тип контейнера. Подбирается через
     * {@link Layout#detect}. Координаты — внутри (leftPos, topPos), как и слоты.
     */
    static final class Layout {
        String header = "ОПИСЬ";
        String kicker = "ФАЙЛ · УЗЕЛ";
        Section[] sections = new Section[0];
        Stamp[] stamps = new Stamp[0];
        Arrow[] arrows = new Arrow[0];
        Doodle[] doodles = new Doodle[0];
        Annotation annotation = null;

        record Section(String label, int x, int y, float startT) {}
        record Stamp(String text, int cx, int cy, float rotation, int color, float startT) {}
        record Arrow(int x1, int y1, int x2, int y2, float startT) {}
        record Annotation(String text, int x, int y, int targetX, int targetY) {}

        @FunctionalInterface
        interface DoodleDrawer { void draw(GuiGraphics gfx, int x, int y, float progress); }
        record Doodle(int x, int y, float startT, DoodleDrawer drawer) {}

        static Layout detect(AbstractContainerScreen<?> screen) {
            String cls = screen.getClass().getSimpleName();
            String title = screen.getTitle().getString();
            return switch (cls) {
                case "CraftingScreen" -> craftingLayout();
                case "FurnaceScreen" -> furnaceLayout("ПЕЧЬ", "СПЛАВ · ОБЖИГ");
                case "BlastFurnaceScreen" -> furnaceLayout("ДОМНА", "ПЛАВКА · РУДА");
                case "SmokerScreen" -> furnaceLayout("КОПТИЛЬНЯ", "ОБРАБОТКА · СЫРЬЁ");
                case "BrewingStandScreen" -> brewingLayout();
                case "AnvilScreen" -> anvilLayout();
                case "EnchantmentScreen" -> enchantLayout();
                case "GrindstoneScreen" -> grindstoneLayout();
                case "LoomScreen" -> loomLayout();
                case "StonecutterScreen" -> stonecutterLayout();
                case "CartographyTableScreen" -> cartographyLayout();
                case "SmithingScreen" -> smithingLayout();
                case "ShulkerBoxScreen", "ChestScreen", "DispenserScreen", "HopperScreen" ->
                        storageLayout(title);
                default -> genericLayout(title);
            };
        }

        // ----------------------- конкретные раскладки -----------------------

        private static Layout craftingLayout() {
            Layout L = new Layout();
            L.header = "ВЕРСТАК";
            L.kicker = "ПРОЕКТ · ЧЕРТЁЖ №" + dailySerial();
            L.sections = new Section[]{
                    new Section("СОСТАВ", 30, 24, 0.42f),
                    new Section("ВЫХОД", 122, 24, 0.50f),
            };
            L.arrows = new Arrow[]{
                    new Arrow(91, 35, 122, 35, 0.65f),
            };
            L.stamps = new Stamp[]{
                    new Stamp("ОПИСЬ", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            L.doodles = new Doodle[]{
                    new Doodle(10, 75, 0.62f, PaperContainerRender::doodlePencil),
                    new Doodle(160, 60, 0.66f, PaperContainerRender::doodleAsterisk),
            };
            L.annotation = new Annotation("результат", 138, 14, 132, 32);
            return L;
        }

        private static Layout furnaceLayout(String header, String kicker) {
            Layout L = new Layout();
            L.header = header;
            L.kicker = kicker;
            L.sections = new Section[]{
                    new Section("СЫРЬЁ", 50, 22, 0.42f),
                    new Section("ТОПЛИВО", 38, 60, 0.48f),
                    new Section("ИЗДЕЛИЕ", 110, 22, 0.54f),
            };
            L.arrows = new Arrow[]{
                    new Arrow(75, 35, 110, 35, 0.66f),
            };
            L.stamps = new Stamp[]{
                    new Stamp("ОБРАБОТКА", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            L.doodles = new Doodle[]{
                    new Doodle(58, 53, 0.62f, PaperContainerRender::doodleFlame),
            };
            L.annotation = new Annotation("уголь / лава", 8, 56, 50, 56);
            return L;
        }

        private static Layout brewingLayout() {
            Layout L = new Layout();
            L.header = "АЛХИМИЯ";
            L.kicker = "ВАРКА · РЕЦЕПТ";
            L.sections = new Section[]{
                    new Section("РЕАГЕНТ", 70, 7, 0.42f),
                    new Section("ПЛАМЯ", 6, 50, 0.48f),
                    new Section("СОСУДЫ", 50, 60, 0.54f),
            };
            L.stamps = new Stamp[]{
                    new Stamp("ОПЫТ", 144, 95, -8f, PaperRender.INK_RED, 0.78f),
            };
            L.doodles = new Doodle[]{
                    new Doodle(20, 38, 0.62f, PaperContainerRender::doodleAsterisk),
            };
            return L;
        }

        private static Layout anvilLayout() {
            Layout L = new Layout();
            L.header = "КУЗНЯ";
            L.kicker = "РЕМОНТ · НАМЕТКА";
            L.sections = new Section[]{
                    new Section("ОСНОВА", 21, 26, 0.42f),
                    new Section("МАТЕРИАЛ", 56, 26, 0.48f),
                    new Section("ИЗДЕЛИЕ", 130, 26, 0.54f),
            };
            L.arrows = new Arrow[]{
                    new Arrow(91, 47, 130, 47, 0.66f),
            };
            L.stamps = new Stamp[]{
                    new Stamp("СВАРКА", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        private static Layout enchantLayout() {
            Layout L = new Layout();
            L.header = "ОБРЯД";
            L.kicker = "СИМВОЛЫ · ЗАКЛИНАНИЕ";
            L.stamps = new Stamp[]{
                    new Stamp("ТАЙНЫЙ ЯЗЫК", 144, 95, -8f, PaperRender.INK_RED, 0.78f),
            };
            L.doodles = new Doodle[]{
                    new Doodle(14, 22, 0.62f, PaperContainerRender::doodleAsterisk),
                    new Doodle(150, 22, 0.66f, PaperContainerRender::doodleAsterisk),
            };
            return L;
        }

        private static Layout grindstoneLayout() {
            Layout L = new Layout();
            L.header = "ТОЧИЛО";
            L.kicker = "СНЯТИЕ · ОЧИЩЕНИЕ";
            L.stamps = new Stamp[]{
                    new Stamp("ОЧИЩЕНО", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        private static Layout loomLayout() {
            Layout L = new Layout();
            L.header = "ТКАЦКИЙ";
            L.kicker = "ЗНАМЯ · УЗОР";
            L.stamps = new Stamp[]{
                    new Stamp("УЗОР", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        private static Layout stonecutterLayout() {
            Layout L = new Layout();
            L.header = "КАМНЕРЕЗ";
            L.kicker = "РАСПИЛ · ЗАГОТОВКА";
            L.stamps = new Stamp[]{
                    new Stamp("РЕЗ", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        private static Layout cartographyLayout() {
            Layout L = new Layout();
            L.header = "КАРТОГРАФИЯ";
            L.kicker = "СВЕДЕНИЯ · РАЙОН";
            L.stamps = new Stamp[]{
                    new Stamp("УТВЕРЖДЕНО", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        private static Layout smithingLayout() {
            Layout L = new Layout();
            L.header = "СМИТИНГ";
            L.kicker = "СБОРКА · ШАБЛОН";
            L.stamps = new Stamp[]{
                    new Stamp("СБОР", 144, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        private static Layout storageLayout(String title) {
            Layout L = new Layout();
            String upper = title.toUpperCase();
            L.header = upper.length() > 22 ? upper.substring(0, 22) : upper;
            L.kicker = "ОПИСЬ · СОДЕРЖИМОЕ";
            L.sections = new Section[]{
                    new Section("СОДЕРЖИМОЕ", 8, 22, 0.42f),
            };
            L.stamps = new Stamp[]{
                    new Stamp("ОПИСЬ", 132, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            L.doodles = new Doodle[]{
                    new Doodle(10, 60, 0.62f, PaperContainerRender::doodleAsterisk),
            };
            return L;
        }

        private static Layout genericLayout(String title) {
            Layout L = new Layout();
            String upper = title.toUpperCase();
            L.header = upper.length() > 22 ? upper.substring(0, 22) : (upper.isEmpty() ? "ТЕРМИНАЛ" : upper);
            L.kicker = "ФАЙЛ · УЗЕЛ";
            L.stamps = new Stamp[]{
                    new Stamp("ОПИСЬ", 132, 95, -6f, PaperRender.INK_RED, 0.78f),
            };
            return L;
        }

        /** Серийный номер на основе игрового дня — каждый день меняется, но стабилен в течение дня. */
        private static String dailySerial() {
            try {
                long t = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getDayTime() / 24000L;
                return String.format("%04d", Math.floorMod(t * 47L + 13L, 9000L) + 1000L);
            } catch (Throwable ignored) {
                return "0047";
            }
        }
    }

    // ============================== РИСУНКИ-«ДУДЛЫ» ==============================

    /** Маленький карандаш — символ заметки. */
    private static void doodlePencil(GuiGraphics gfx, int x, int y, float progress) {
        int alpha = (int) (255 * progress);
        int col = (alpha << 24) | (PaperRender.INK_FADED & 0xFFFFFF);
        // тело карандаша
        gfx.fill(x, y, x + 12, y + 2, col);
        // острие
        gfx.fill(x - 2, y, x, y + 2, (alpha << 24) | 0x666666);
        // ластик
        gfx.fill(x + 12, y, x + 14, y + 2,
                (alpha << 24) | (PaperRender.INK_RED & 0xFFFFFF));
    }

    /** Маленькая «искорка» — звёздочка-астерикс. */
    private static void doodleAsterisk(GuiGraphics gfx, int x, int y, float progress) {
        int alpha = (int) (255 * progress);
        int col = (alpha << 24) | (PaperRender.INK_FADED & 0xFFFFFF);
        gfx.fill(x - 3, y, x + 4, y + 1, col);
        gfx.fill(x, y - 3, x + 1, y + 4, col);
        gfx.fill(x - 2, y - 2, x - 1, y - 1, col);
        gfx.fill(x + 2, y - 2, x + 3, y - 1, col);
        gfx.fill(x - 2, y + 2, x - 1, y + 3, col);
        gfx.fill(x + 2, y + 2, x + 3, y + 3, col);
    }

    /** Стилизованный язычок пламени — для печи. */
    private static void doodleFlame(GuiGraphics gfx, int x, int y, float progress) {
        int alpha = (int) (255 * progress);
        int outer = (alpha << 24) | (PaperRender.INK_RED & 0xFFFFFF);
        int inner = (alpha << 24) | 0xC8862F;
        // абрис
        gfx.fill(x + 1, y, x + 5, y + 1, outer);
        gfx.fill(x, y + 1, x + 6, y + 5, outer);
        gfx.fill(x + 1, y + 5, x + 5, y + 7, outer);
        // язычки изнутри
        gfx.fill(x + 2, y + 2, x + 4, y + 5, inner);
        gfx.fill(x + 3, y + 1, x + 4, y + 2, inner);
    }
}
