package com.otbor.client;

import com.labyrinthmod.common.generation.LabyrinthConfig;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class LabyrinthConfigScreen extends Screen {
    private final Screen parent;
    private LabyrinthConfig config;
    private EditBox gleydRadiusBox, mainMazeWidthBox, sectorWidthBox, wallThicknessBox;
    private EditBox mazeFloorYBox, mainMazeHeightBox, sectorHeightBox, separatorWallHeightBox;
    private EditBox undergroundDepthBox, mainMazeCellSizeBox, sectorCellSizeBox;

    public LabyrinthConfigScreen(Screen parent, LabyrinthConfig config) {
        super(Component.literal("Настройка Лабиринта"));
        this.parent = parent;
        this.config = config;
    }

    @Override
    protected void init() {
        super.init();
        int paperW = Math.min(this.width - 80, 800);
        int paperX = (this.width - paperW) / 2;
        int paperY = 40;
        int boxW = 60, startX = paperX + 40, startY = paperY + 80, gapX = 140, gapY = 30;

        gleydRadiusBox = createBox(startX, startY, boxW, String.valueOf(config.gleydRadius));
        mainMazeWidthBox = createBox(startX + gapX, startY, boxW, String.valueOf(config.mainMazeWidth));
        sectorWidthBox = createBox(startX + gapX * 2, startY, boxW, String.valueOf(config.sectorWidth));
        wallThicknessBox = createBox(startX + gapX * 3, startY, boxW, String.valueOf(config.wallThickness));

        mazeFloorYBox = createBox(startX, startY + gapY, boxW, String.valueOf(config.mazeFloorY));
        mainMazeHeightBox = createBox(startX + gapX, startY + gapY, boxW, String.valueOf(config.mainMazeHeight));
        sectorHeightBox = createBox(startX + gapX * 2, startY + gapY, boxW, String.valueOf(config.sectorHeight));
        separatorWallHeightBox = createBox(startX + gapX * 3, startY + gapY, boxW, String.valueOf(config.separatorWallHeight));

        undergroundDepthBox = createBox(startX, startY + gapY * 2, boxW, String.valueOf(config.undergroundDepth));
        mainMazeCellSizeBox = createBox(startX + gapX, startY + gapY * 2, boxW, String.valueOf(config.mainMazeCellSize));
        sectorCellSizeBox = createBox(startX + gapX * 2, startY + gapY * 2, boxW, String.valueOf(config.sectorCellSize));

        addRenderableWidget(Button.builder(Component.literal("СОХРАНИТЬ И СОЗДАТЬ"), b -> saveAndOpen())
                .bounds(paperX + paperW - 200, paperY + this.height - 120, 180, 26).build());
        addRenderableWidget(Button.builder(Component.literal("<- НАЗАД"), b -> minecraft.setScreen(parent))
                .bounds(paperX + 20, paperY + this.height - 120, 100, 26).build());
    }

    private EditBox createBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(this.font, x, y, w, 20, Component.literal(""));
        box.setValue(value); box.setBordered(false);
        addRenderableWidget(box);
        return box;
    }

    private void saveAndOpen() {
        config.gleydRadius = parseInt(gleydRadiusBox.getValue(), 70);
        config.mainMazeWidth = parseInt(mainMazeWidthBox.getValue(), 6);
        config.sectorWidth = parseInt(sectorWidthBox.getValue(), 8);
        config.wallThickness = parseInt(wallThicknessBox.getValue(), 2);
        config.mazeFloorY = parseInt(mazeFloorYBox.getValue(), 32);
        config.mainMazeHeight = parseInt(mainMazeHeightBox.getValue(), 50);
        config.sectorHeight = parseInt(sectorHeightBox.getValue(), 70);
        config.separatorWallHeight = parseInt(separatorWallHeightBox.getValue(), 80);
        config.undergroundDepth = parseInt(undergroundDepthBox.getValue(), 130);
        config.mainMazeCellSize = parseInt(mainMazeCellSizeBox.getValue(), 6);
        config.sectorCellSize = parseInt(sectorCellSizeBox.getValue(), 8);

        config.save(); // Сохраняем настройки в JSON!
        CreateWorldScreen.openFresh(minecraft, parent); // Открываем ванильное меню создания мира
    }

    private int parseInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);
        int paperW = Math.min(this.width - 80, 800), paperH = this.height - 80;
        int paperX = (this.width - paperW) / 2, paperY = 40;

        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW / 2f, paperY + paperH / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.4f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, 1f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 16, 12, false);
        PaperRender.drawPin(gfx, paperW - 16, 12, true);

        Font font = this.font;
        gfx.drawString(font, "НАСТРОЙКА ЛАБИРИНТА", paperW / 2 - font.width("НАСТРОЙКА ЛАБИРИНТА") / 2, 20, PaperRender.INK_DARK, false);

        int startX = 40, startY = 80, gapX = 140, gapY = 30, labelColor = PaperRender.INK_SOFT;
        gfx.drawString(font, "Радиус глейда:", startX, startY - 12, labelColor, false);
        gfx.drawString(font, "Толщина осн.:", startX + gapX, startY - 12, labelColor, false);
        gfx.drawString(font, "Толщина сектора:", startX + gapX * 2, startY - 12, labelColor, false);
        gfx.drawString(font, "Толщина стен:", startX + gapX * 3, startY - 12, labelColor, false);

        gfx.drawString(font, "Y пола:", startX, startY + gapY - 12, labelColor, false);
        gfx.drawString(font, "Высота осн.:", startX + gapX, startY + gapY - 12, labelColor, false);
        gfx.drawString(font, "Высота сектора:", startX + gapX * 2, startY + gapY - 12, labelColor, false);
        gfx.drawString(font, "Высота стены:", startX + gapX * 3, startY + gapY - 12, labelColor, false);

        gfx.drawString(font, "Глубина недр:", startX, startY + gapY * 2 - 12, labelColor, false);
        gfx.drawString(font, "Ячейка осн.:", startX + gapX, startY + gapY * 2 - 12, labelColor, false);
        gfx.drawString(font, "Ячейка сектора:", startX + gapX * 2, startY + gapY * 2 - 12, labelColor, false);

        gfx.pose().popPose();
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}