package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.menu.WritableTaskMenu;
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

    @Override
    protected void init() {
        super.init();

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        titleEditBox = new EditBox(this.font, x + 20, y + 30, 216, 20, Component.literal("Title"));
        titleEditBox.setMaxLength(50);
        this.addWidget(titleEditBox);
        this.setInitialFocus(titleEditBox);

        descriptionEditBox = new EditBox(this.font, x + 20, y + 70, 216, 80, Component.literal("Description"));
        descriptionEditBox.setMaxLength(500);
        this.addWidget(descriptionEditBox);

        rewardEditBox = new EditBox(this.font, x + 20, y + 170, 216, 20, Component.literal("Reward"));
        rewardEditBox.setMaxLength(100);
        this.addWidget(rewardEditBox);

        saveButton = Button.builder(Component.literal("Сохранить"), btn -> saveTask())
                .bounds(x + 20, y + 210, 100, 20)
                .build();
        this.addWidget(saveButton);

        cancelButton = Button.builder(Component.literal("Отмена"), btn -> this.onClose())
                .bounds(x + 136, y + 210, 100, 20)
                .build();
        this.addWidget(cancelButton);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xDD1a1a1a);
        guiGraphics.fill(x + 2, y + 2, x + this.imageWidth - 2, y + this.imageHeight - 2, 0xFF2d2d2d);
    }

    private void saveTask() {
        String title = titleEditBox.getValue().trim();
        String description = descriptionEditBox.getValue().trim();
        String reward = rewardEditBox.getValue().trim();

        if (!title.isEmpty() && !description.isEmpty()) {
            menu.saveAndConvert(title, description, reward);
            this.onClose();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.drawString(this.font, "Название задания:", x + 20, y + 18, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Описание:", x + 20, y + 58, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Награда:", x + 20, y + 158, 0xFFFFFF);

        this.renderTooltip(guiGraphics, mouseX, mouseY);
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