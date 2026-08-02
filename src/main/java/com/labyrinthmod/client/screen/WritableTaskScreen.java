package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.menu.WritableTaskMenu;
import com.otbor.client.widgets.PaperButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class WritableTaskScreen extends AbstractContainerScreen<WritableTaskMenu> {
    private EditBox titleEditBox;
    private EditBox descriptionEditBox;
    private EditBox rewardEditBox;
    private Button saveButton;
    private Button cancelButton;

    public WritableTaskScreen(WritableTaskMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 270;
    }

    private final int paperW = 300;
    private final int paperH = 280;

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.paperW) / 2;
        int y = (this.height - this.paperH) / 2;

        // Поля ввода без рамок (имитация письма на бумаге)
        titleEditBox = new EditBox(this.font, x + 20, y + 45, 260, 20, Component.literal("Title"));
        titleEditBox.setMaxLength(50);
        titleEditBox.setTextColor(0xFF2A1810);
        titleEditBox.setBordered(false);
        this.addWidget(titleEditBox);
        this.setInitialFocus(titleEditBox);

        descriptionEditBox = new EditBox(this.font, x + 20, y + 85, 260, 60, Component.literal("Description"));
        descriptionEditBox.setMaxLength(500);
        descriptionEditBox.setTextColor(0xFF2A1810);
        descriptionEditBox.setBordered(false);
        this.addWidget(descriptionEditBox);

        rewardEditBox = new EditBox(this.font, x + 20, y + 175, 260, 20, Component.literal("XpReward"));
        rewardEditBox.setMaxLength(10);
        rewardEditBox.setFilter(s -> s.matches("\\d*")); // Только цифры
        rewardEditBox.setTextColor(0xFF2A1810);
        rewardEditBox.setBordered(false);
        this.addWidget(rewardEditBox);

        // Кнопка "Сохранить" в стиле бумаги
        PaperButton saveButton = new PaperButton(
                x + 30, y + 230, 110, 24,
                Component.literal("Сохранить"),
                btn -> saveTask()
        );
        this.addRenderableWidget(saveButton);

        // Кнопка "Отмена" в стиле бумаги
        PaperButton cancelButton = new PaperButton(
                x + 160, y + 230, 110, 24,
                Component.literal("Отмена"),
                btn -> this.onClose()
        );
        this.addRenderableWidget(cancelButton);
    }

    private void saveTask() {
        String title = titleEditBox.getValue().trim();
        String description = descriptionEditBox.getValue().trim();
        String rewardStr = rewardEditBox.getValue().trim();

        int xpReward = 0;
        if (!rewardStr.isEmpty()) {
            try {
                xpReward = Integer.parseInt(rewardStr);
            } catch (NumberFormatException e) {
                xpReward = 0;
            }
        }

        if (!title.isEmpty() && !description.isEmpty()) {
            menu.saveAndConvert(title, description, xpReward);
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int x = (this.width - this.paperW) / 2;
        int y = (this.height - this.paperH) / 2;

        // 1. Тень бумаги
        guiGraphics.fill(x - 4, y - 4, x + this.paperW + 4, y + this.paperH + 4, 0x80000000);
        // 2. Основная бумага
        guiGraphics.fill(x, y, x + this.paperW, y + this.paperH, 0xFFE9DCB9);

        // 3. Окантовка
        int borderColor = 0xFF6B5842;
        guiGraphics.fill(x, y, x + this.paperW, y + 2, borderColor);
        guiGraphics.fill(x, y + this.paperH - 2, x + this.paperW, y + this.paperH, borderColor);
        guiGraphics.fill(x, y, x + 2, y + this.paperH, borderColor);
        guiGraphics.fill(x + this.paperW - 2, y, x + this.paperW, y + this.paperH, borderColor);

        int padX = 20;

        // Заголовок экрана
        String screenTitle = "Создание задания";
        int titleWidth = this.font.width(screenTitle);
        guiGraphics.drawString(this.font, screenTitle, x + (this.paperW - titleWidth) / 2, y + 20, 0xFF7A1F1F, false);

        // Линия-разделитель под заголовком
        guiGraphics.fill(x + padX, y + 36, x + this.paperW - padX, y + 37, 0xFF7A1F1F);

        // Подписи к полям ввода (цвет чернил)
        guiGraphics.drawString(this.font, "Название задания:", x + padX, y + 30, 0xFF2A1810, false);
        guiGraphics.drawString(this.font, "Описание:", x + padX, y + 70, 0xFF2A1810, false);
        guiGraphics.drawString(this.font, "Награда (уровни опыта):", x + padX, y + 160, 0xFF2A1810, false);

        // Рендерим поля ввода и кнопки поверх бумаги
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xDD1a1a1a);
        guiGraphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFF2d2d2d);
    }





    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}