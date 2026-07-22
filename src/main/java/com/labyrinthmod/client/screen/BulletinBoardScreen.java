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

    private static final int[][] CARD_POSITIONS = {
            {70, 60},
            {190, 60},
            {310, 60},
            {130, 170},
            {250, 170}
    };

    private static final int[] SLOT_INDICES = {0, 1, 2, 3, 4};

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

        // Фон доски
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFE8DCC4);
        guiGraphics.renderOutline(x, y, this.imageWidth, this.imageHeight, 0xFF8B7355);

        // Заголовок рисуем ТОЛЬКО здесь (один раз)
        guiGraphics.drawString(this.font, this.title, x + this.titleLabelX, y + this.titleLabelY, 0x333333, false);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // ПУСТО - заголовок уже нарисован в renderBg
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int guiX = (this.width - this.imageWidth) / 2;
        int guiY = (this.height - this.imageHeight) / 2;

        renderBg(guiGraphics, partialTick, mouseX, mouseY);
        renderTaskCards(guiGraphics, guiX, guiY, mouseX, mouseY);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTaskCards(GuiGraphics guiGraphics, int guiX, int guiY, int mouseX, int mouseY) {
        BulletinBoardBlockEntity blockEntity = this.menu.getBlockEntity();
        if (blockEntity == null) return;

        hoveredCardIndex = -1;

        for (int i = 0; i < CARD_POSITIONS.length; i++) {
            int cardX = guiX + CARD_POSITIONS[i][0];
            int cardY = guiY + CARD_POSITIONS[i][1];
            int slotIndex = SLOT_INDICES[i];

            ItemStack taskStack = blockEntity.getTask(slotIndex);

            boolean isHovered = mouseX >= cardX && mouseX < cardX + CARD_WIDTH &&
                    mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;

            if (isHovered) {
                hoveredCardIndex = i;
            }

            drawTaskCard(guiGraphics, cardX, cardY, taskStack, isHovered);

            if (isHovered && !taskStack.isEmpty()) {
                guiGraphics.renderTooltip(this.font, taskStack, mouseX, mouseY);
            }
        }
    }

    private void drawTaskCard(GuiGraphics guiGraphics, int x, int y, ItemStack stack, boolean isHovered) {
        boolean isEmpty = stack.isEmpty();

        int bgColor = isEmpty ? 0xFF808080 : 0xFF909090;
        int highlightColor = isHovered ? 0xFFb0b0b0 : 0xFFa0a0a0;
        int shadowColor = 0xFF606060;

        guiGraphics.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, bgColor);

        int borderColor = isHovered ? 0xFFFFFFFF : 0xFFaaaaaa;
        guiGraphics.renderOutline(x, y, CARD_WIDTH, CARD_HEIGHT, borderColor);

        guiGraphics.hLine(x + 1, x + CARD_WIDTH - 2, y + 1, highlightColor);
        guiGraphics.vLine(x + 1, y + 1, y + CARD_HEIGHT - 2, highlightColor);
        guiGraphics.hLine(x + 1, x + CARD_WIDTH - 2, y + CARD_HEIGHT - 2, shadowColor);
        guiGraphics.vLine(x + CARD_WIDTH - 2, y + 1, y + CARD_HEIGHT - 2, shadowColor);

        if (!isEmpty && stack.getItem() instanceof TaskItem) {
            guiGraphics.renderItem(stack, x + CARD_WIDTH / 2 - 8, y + CARD_HEIGHT / 2 - 8);

            String title = ((TaskItem) stack.getItem()).getTitle(stack);
            if (title != null && !title.isEmpty()) {
                if (this.font.width(title) > CARD_WIDTH - 10) {
                    title = this.font.plainSubstrByWidth(title, CARD_WIDTH - 10) + "...";
                }
                guiGraphics.drawString(this.font, title, x + (CARD_WIDTH - this.font.width(title)) / 2, y + CARD_HEIGHT - 15, 0x000000, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredCardIndex >= 0) {
            int slotIndex = SLOT_INDICES[hoveredCardIndex];
            BulletinBoardBlockEntity blockEntity = this.menu.getBlockEntity();

            if (blockEntity != null) {
                ItemStack taskStack = blockEntity.getTask(slotIndex);
                if (!taskStack.isEmpty()) {
                    this.minecraft.setScreen(new TaskViewScreen(taskStack, () -> {}));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Тултипы уже рисуются в renderTaskCards
    }
}