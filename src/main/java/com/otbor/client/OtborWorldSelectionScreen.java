package com.otbor.client;

import com.labyrinthmod.common.generation.LabyrinthConfig;
import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class OtborWorldSelectionScreen extends Screen {
    private final Screen parent;
    private List<LevelSummary> worlds = new ArrayList<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private long openedAt = -1L;
    private int hoveredWorldIndex = -1;
    private int hoveredButton = -1;

    // Параметры списка
    private int listAbsX, listAbsY, listW, listH;
    private int itemH = 42;

    public OtborWorldSelectionScreen(Screen parent) {
        super(Component.literal("ВЫБОР МИРА"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        boolean fileExists = minecraft.getResourceManager()
                .getResource(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("labyrinthmod", "worldgen/world_preset/labyrinth.json"))
                .isPresent();
        System.out.println("==================================================");
        System.out.println(" [DEBUG] ИГРА ВИДИТ ФАЙЛ labyrinth.json: " + fileExists);
        System.out.println("==================================================");
        openedAt = System.currentTimeMillis();
        loadWorlds();

        int paperW = Math.min(this.width - 80, 680);
        int paperX = (this.width - paperW) / 2;
        int paperTop = 70;

        // Кнопка НАЗАД в стиле Paper
        addRenderableWidget(PaperWidgets.paperButton(
                paperX + 20, paperTop + 8, 100, 26,
                Component.literal(" <- НАЗАД "),
                b -> minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, -1.5f));
        // Кнопка СОЗДАТЬ МИР в стиле Paper
        addRenderableWidget(PaperWidgets.paperButton(
                paperX + paperW - 140, paperTop + 8, 120, 26,
                Component.literal( "+ СОЗДАТЬ МИР  "),
                b -> minecraft.setScreen(new LabyrinthCreateWorldScreen(this)), // <-- ОТКРЫВАЕМ НАШЕ МЕНЮ
                0L, PaperRender.INK_RED, 1.5f));
    }

    private void loadWorlds() {
        worlds.clear();
        try {
            LevelStorageSource source = minecraft.getLevelSource();
            var candidates = source.findLevelCandidates();
            List<LevelSummary> levels = source.loadLevelSummaries(candidates).join();
            worlds.addAll(levels);
            worlds.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        long age = Math.max(0, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 380f));
        float pulse = 0.5f + 0.5f * (float) Math.sin(PaperRender.gameTime() * 0.05f);

        int paperW = Math.min(this.width - 80, 680);
        int paperH = this.height - 100;
        int paperX = (this.width - paperW) / 2;
        int paperY = 45;

        int slide = (int) ((1f - appear) * 14f);

        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW / 2f, paperY + paperH / 2f + slide, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.6f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, appear, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 16, 12, false);
        PaperRender.drawPin(gfx, paperW - 16, 12, true);

        Font font = this.font;

        // Уникальный кикер
        String kicker = "ФАЙЛ №05 · РЕЕСТР МИРОВ · УРОВЕНЬ ДОСТУПА 3";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, paperW / 2 - kw / 2, 8,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        // Заголовок с глитч-эффектом
        String title = "РЕЕСТР МИРОВ";
        float titleScale = 2.2f;
        int titleW = (int) (font.width(title) * titleScale);
        int titleY = 24;
        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - titleW / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        int glitch = (int) (60 + 50 * pulse);
        gfx.drawString(font, title, 1, 0, (glitch << 24) | 0x7A1F1F, false);
        gfx.drawString(font, title, -1, 0, (glitch << 24) | 0x226622, false);
        PaperRender.drawInkText(gfx, font, title, 0, 0, PaperRender.INK_DARK);
        gfx.pose().popPose();

        // Подзаголовок
        String subtitle = "доступные локальные миры";
        int subW = font.width(subtitle);
        gfx.drawString(font, subtitle, paperW / 2 - subW / 2, titleY + 28,
                PaperRender.INK_SOFT, false);

        // Декоративная разделительная линия
        int lineY = titleY + 42;
        int lineW = paperW - 100;
        int lineColor = PaperRender.withAlpha(PaperRender.INK_RED, 0.55f + 0.45f * pulse);
        PaperRender.drawHandDivider(gfx, paperW / 2 - lineW / 2, lineY, lineW,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.6f));

        // Статистическая информация
        String stats = "всего миров: " + worlds.size() + " · последний вход: " + getLastPlayedDate();
        gfx.drawString(font, stats, paperW / 2 - font.width(stats) / 2, lineY + 12,
                PaperRender.INK_FADED, false);

        gfx.pose().popPose();

        // Список миров
        listW = paperW - 60;
        listH = paperH - 170;
        listAbsX = paperX + 30;
        listAbsY = paperY + 130;

        int totalContentH = worlds.size() * itemH;
        maxScroll = Math.max(0, totalContentH - listH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Обрезка списка
        gfx.enableScissor(listAbsX, listAbsY, listAbsX + listW, listAbsY + listH);

        int startIndex = scrollOffset / itemH;
        int endIndex = Math.min(worlds.size(), startIndex + (listH / itemH) + 2);

        hoveredWorldIndex = -1;
        hoveredButton = -1;

        for (int i = startIndex; i < endIndex; i++) {
            LevelSummary world = worlds.get(i);
            int itemY = listAbsY + i * itemH - scrollOffset;

            if (itemY + itemH < listAbsY || itemY > listAbsY + listH) continue;

            boolean isHovered = mouseX >= listAbsX && mouseX <= listAbsX + listW
                    && mouseY >= itemY && mouseY <= itemY + itemH;

            // Фон строки с эффектом при наведении
            int bgColor;
            if (isHovered) {
                bgColor = PaperRender.withAlpha(PaperRender.PAPER_BASE, 0.55f);
                // Добавляем красный маркер слева
                gfx.fill(listAbsX, itemY, listAbsX + 3, itemY + itemH, PaperRender.INK_RED);
            } else {
                bgColor = 0x18000000;
                gfx.fill(listAbsX, itemY, listAbsX + 1, itemY + itemH, PaperRender.INK_FADED);
            }
            gfx.fill(listAbsX + 3, itemY, listAbsX + listW, itemY + itemH, bgColor);

            // Иконка мира (стилизованная папка или компас)
            drawWorldIcon(gfx, listAbsX + 8, itemY + 4, 20);

            // Название мира
            String name = world.getLevelName();
            if (name.isEmpty()) name = "Безымянный мир";
            gfx.drawString(font, name, listAbsX + 36, itemY + 6, PaperRender.INK, false);

            // Дата последнего запуска
            String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date(world.getLastPlayed()));
            gfx.drawString(font, dateStr, listAbsX + 36, itemY + 20, PaperRender.INK_FADED, false);

            // Версия мира
            String version = "1.20.1";
            gfx.drawString(font, version, listAbsX + 36, itemY + 32, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f), false);

            if (world.isDisabled()) {
                gfx.drawString(font, "⚠ НЕСОВМЕСТИМА", listAbsX + 180, itemY + 14, PaperRender.INK_RED, false);
            }

            // Кнопки действий
            int btnH = 24;
            int btnW = 80;
            int btnY = itemY + (itemH - btnH) / 2;
            int btnGap = 6;

            // Кнопка ИГРАТЬ
            int playBtnX = listAbsX + listW - btnW - 8;
            boolean playHover = mouseX >= playBtnX && mouseX <= playBtnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH;
            drawStyledButton(gfx, font, playBtnX, btnY, btnW, btnH,
                    world.isDisabled() ? "ЗАБЛОКИРОВАН" : "▶ ИГРАТЬ",
                    playHover && !world.isDisabled(), playHover);
            if (playHover && isHovered && !world.isDisabled()) {
                hoveredWorldIndex = i;
                hoveredButton = 0;
            }

            // Кнопка РЕДАКТИРОВАТЬ
            int editBtnW = 28;
            int editBtnX = playBtnX - editBtnW - btnGap;
            boolean editHover = mouseX >= editBtnX && mouseX <= editBtnX + editBtnW
                    && mouseY >= btnY && mouseY <= btnY + btnH;
            drawStyledButton(gfx, font, editBtnX, btnY, editBtnW, btnH, "✎", editHover, editHover);
            if (editHover && isHovered) {
                hoveredWorldIndex = i;
                hoveredButton = 1;
            }

            // Кнопка УДАЛИТЬ
            int delBtnX = editBtnX - editBtnW - btnGap;
            boolean delHover = mouseX >= delBtnX && mouseX <= delBtnX + editBtnW
                    && mouseY >= btnY && mouseY <= btnY + btnH;
            drawStyledButton(gfx, font, delBtnX, btnY, editBtnW, btnH, "🗑", delHover, delHover);
            if (delHover && isHovered) {
                hoveredWorldIndex = i;
                hoveredButton = 2;
            }
        }

        gfx.disableScissor();

        // Скроллбар
        if (maxScroll > 0) {
            int scrollBarH = Math.max(20, (listH * listH) / totalContentH);
            int scrollBarY = listAbsY + (int) ((float) scrollOffset / maxScroll * (listH - scrollBarH));
            gfx.fill(listAbsX + listW + 4, scrollBarY, listAbsX + listW + 7, scrollBarY + scrollBarH,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.7f));
            gfx.fill(listAbsX + listW + 3, scrollBarY, listAbsX + listW + 4, scrollBarY + scrollBarH,
                    PaperRender.withAlpha(PaperRender.INK, 0.5f));
        }

        // Декоративные элементы по углам
        renderCornerDecorations(gfx, paperX, paperY, paperW, paperH);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawWorldIcon(GuiGraphics gfx, int x, int y, int size) {
        int ink = PaperRender.INK;
        int soft = PaperRender.INK_SOFT;

        // Стилизованная папка/мир
        gfx.fill(x, y + 4, x + size, y + size, PaperRender.withAlpha(soft, 0.3f));
        gfx.fill(x, y + 4, x + size, y + 5, ink);
        gfx.fill(x, y + size - 1, x + size, y + size, ink);
        gfx.fill(x, y + 4, x + 1, y + size, ink);
        gfx.fill(x + size - 1, y + 4, x + size, y + size, ink);

        // Земля/компас внутри
        gfx.fill(x + 4, y + 8, x + size - 4, y + size - 4, soft);
        gfx.fill(x + size/2, y + 6, x + size/2 + 1, y + 12, PaperRender.INK_RED);
        gfx.fill(x + 6, y + size/2, x + size - 4, y + size/2 + 1, PaperRender.INK_RED);
    }

    private void drawStyledButton(GuiGraphics gfx, Font font, int x, int y, int w, int h,
                                  String text, boolean hovered, boolean isHoverAnim) {
        int bg = hovered ? PaperRender.PAPER_BASE : PaperRender.PAPER_DARK;
        int border = hovered ? PaperRender.INK_RED : PaperRender.INK_SOFT;

        // Тень кнопки
        gfx.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x40000000);

        // Тело кнопки
        gfx.fill(x, y, x + w, y + h, bg);

        // Рамка
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        // Декоративные уголки
        if (hovered) {
            gfx.fill(x + 2, y + 2, x + 4, y + 3, PaperRender.INK_RED);
            gfx.fill(x + w - 4, y + 2, x + w - 2, y + 3, PaperRender.INK_RED);
            gfx.fill(x + 2, y + h - 3, x + 4, y + h - 2, PaperRender.INK_RED);
            gfx.fill(x + w - 4, y + h - 3, x + w - 2, y + h - 2, PaperRender.INK_RED);
        }

        int tw = font.width(text);
        int tx = x + w / 2 - tw / 2;
        int ty = y + (h - 8) / 2;

        if (hovered) {
            // Эффект "приподнятой" кнопки
            gfx.drawString(font, text, tx, ty - 1, PaperRender.withAlpha(PaperRender.INK_RED, 0.5f), false);
        }
        gfx.drawString(font, text, tx, ty, hovered ? PaperRender.INK_RED : PaperRender.INK, false);
    }

    private String getLastPlayedDate() {
        if (worlds.isEmpty()) return "никогда";
        long lastPlayed = worlds.get(0).getLastPlayed();
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date(lastPlayed));
    }

    private void renderCornerDecorations(GuiGraphics gfx, int paperX, int paperY, int paperW, int paperH) {
        Font font = this.font;
        Random rnd = new Random(paperX ^ paperY);

        // Угловая пометка "АРХИВ"
        gfx.pose().pushPose();
        gfx.pose().translate(paperX + 15, paperY + paperH - 28, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4f));
        PaperRender.drawScribble(gfx, font, "архив миров", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));
        gfx.pose().popPose();

        // Штамп "ДОПУЩЕНО" в углу
        if ((PaperRender.gameTime() / 30L) % 6L != 0L) {
            gfx.pose().pushPose();
            gfx.pose().translate(paperX + paperW - 55, paperY + 18, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(7f));
            PaperRender.drawRectStamp(gfx, font, "ДОПУЩЕНО", 0, 0,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.75f));
            gfx.pose().popPose();
        }

        // Декоративная каракуля в правом нижнем углу бумаги
        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW - 90, paperY + paperH - 35, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(3f));
        PaperRender.drawScribble(gfx, font, "лист № " + (Math.abs(rnd.nextInt()) % 999 + 100), 0, 0,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.6f));
        gfx.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hoveredWorldIndex >= 0 && hoveredWorldIndex < worlds.size()) {
            LevelSummary world = worlds.get(hoveredWorldIndex);

            if (hoveredButton == 0 && !world.isDisabled()) {
                minecraft.createWorldOpenFlows().loadLevel(this, world.getLevelId());
                return true;
            }

            if (hoveredButton == 1) {
                try {
                    LevelStorageSource.LevelStorageAccess access =
                            minecraft.getLevelSource().createAccess(world.getLevelId());
                    minecraft.setScreen(new EditWorldScreen((result) -> {
                        if (result) loadWorlds();
                        minecraft.setScreen(this);
                    }, access));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return true;
            }

            if (hoveredButton == 2) {
                minecraft.setScreen(ConfirmWorldDeleteScreen.create(this, world));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listAbsX && mouseX <= listAbsX + listW + 10
                && mouseY >= listAbsY && mouseY <= listAbsY + listH) {
            scrollOffset -= (int) (delta * 18);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}