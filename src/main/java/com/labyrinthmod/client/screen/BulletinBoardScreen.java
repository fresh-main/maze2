package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.item.TaskItem;
import com.labyrinthmod.common.menu.BulletinBoardMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class BulletinBoardScreen extends AbstractContainerScreen<BulletinBoardMenu> {
    private static final int GUI_WIDTH = 400;
    private static final int GUI_HEIGHT = 300;
    private static final int CARD_WIDTH = 80;
    private static final int CARD_HEIGHT = 100;

    // Увеличенное расстояние между карточками
    private static final int[][] CARD_POSITIONS = {
            {35, 55},    // Верхняя левая
            {170, 55},   // Верхняя центральная (было 155, стало 170)
            {305, 55},   // Верхняя правая (было 275, стало 305)
            {105, 160},  // Нижняя левая (было 95, стало 105)
            {240, 160}   // Нижняя правая (было 215, стало 240)
    };

    private int hoveredCardIndex = -1;

    public BulletinBoardScreen(BulletinBoardMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, Component.literal("Доска объявлений"));
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 10;
        this.inventoryLabelY = this.imageHeight + 100;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFE8DCC4);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF8B7355);

        guiGraphics.drawString(this.font, this.title, x + this.titleLabelX, y + this.titleLabelY, 0x333333, false);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int guiX = (this.width - this.imageWidth) / 2;
        int guiY = (this.height - this.imageHeight) / 2;
        renderTaskCards(guiGraphics, guiX, guiY, mouseX, mouseY);
    }

    private void renderTaskCards(GuiGraphics guiGraphics, int guiX, int guiY, int mouseX, int mouseY) {
        BulletinBoardBlockEntity blockEntity = this.menu.getBlockEntity();

        System.out.println("blockEntity in render: " + blockEntity);

        hoveredCardIndex = -1;

        for (int i = 0; i < CARD_POSITIONS.length; i++) {
            int cardX = guiX + CARD_POSITIONS[i][0];
            int cardY = guiY + CARD_POSITIONS[i][1];

            ItemStack taskStack = ItemStack.EMPTY;
            if (blockEntity != null) {
                taskStack = blockEntity.getTask(i);
                System.out.println("Slot " + i + ": " + taskStack);
            }

            boolean isHovered = mouseX >= cardX && mouseX < cardX + CARD_WIDTH &&
                    mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;

            if (isHovered) {
                hoveredCardIndex = i;
                System.out.println("Hovered card: " + i);
            }

            drawTaskCard(guiGraphics, cardX, cardY, taskStack, isHovered);

            if (isHovered && !taskStack.isEmpty()) {
                guiGraphics.renderTooltip(this.font, taskStack, mouseX, mouseY);
            }
        }
    }

    private void drawTaskCard(GuiGraphics guiGraphics, int x, int y, ItemStack stack, boolean isHovered) {
        guiGraphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, 0xFF808080);

        guiGraphics.hLine(x, x + CARD_WIDTH - 1, y, 0xFFC0C0C0);
        guiGraphics.vLine(x, y, y + CARD_HEIGHT - 1, 0xFFC0C0C0);

        guiGraphics.hLine(x, x + CARD_WIDTH - 1, y + CARD_HEIGHT - 1, 0xFF404040);
        guiGraphics.vLine(x + CARD_WIDTH - 1, y, y + CARD_HEIGHT - 1, 0xFF404040);

        if (isHovered) {
            guiGraphics.renderOutline(x - 1, y - 1, CARD_WIDTH + 2, CARD_HEIGHT + 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCardIndex >= 0) {
            BulletinBoardBlockEntity blockEntity = this.menu.getBlockEntity();

            if (blockEntity != null) {
                ItemStack taskStack = blockEntity.getTask(hoveredCardIndex);

                // Открываем TaskViewScreen всегда, даже если слот пустой
                Runnable callback = () -> {
                    if (!taskStack.isEmpty()) {
                        blockEntity.takeTask(hoveredCardIndex, this.minecraft.player);
                    }
                };

                TaskViewScreen taskScreen = new TaskViewScreen(taskStack, callback);
                this.minecraft.setScreen(taskScreen);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}