package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.UpdateBlockNamePacket;
import com.otbor.client.widgets.PaperButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

public class NameableSignalScreen extends Screen {
    private final BlockPos blockPos;
    private EditBox nameEditBox;
    private String currentName;

    public NameableSignalScreen(BlockPos blockPos, String currentName) {
        super(Component.literal("Настройка блока"));
        this.blockPos = blockPos;
        this.currentName = currentName.equals("Без имени") ? "" : currentName;
    }

    @Override
    protected void init() {
        int paperW = 250;
        int paperH = 150;
        int x = (this.width - paperW) / 2;
        int y = (this.height - paperH) / 2;

        nameEditBox = new EditBox(this.font, x + 20, y + 50, 210, 20, Component.literal("Name"));
        nameEditBox.setValue(this.currentName);
        nameEditBox.setMaxLength(64);
        nameEditBox.setTextColor(0xFF111111);
        nameEditBox.setBordered(false);
        this.addRenderableWidget(nameEditBox);
        this.setInitialFocus(nameEditBox);

        PaperButton saveButton = new PaperButton(
                x + 20, y + 90, 100, 24,
                Component.literal("Сохранить"),
                btn -> {
                    NetworkHandler.CHANNEL.sendToServer(new UpdateBlockNamePacket(blockPos, nameEditBox.getValue().trim()));
                    this.onClose();
                }
        );
        this.addRenderableWidget(saveButton);

        PaperButton cancelButton = new PaperButton(
                x + 130, y + 90, 100, 24,
                Component.literal("Отмена"),
                btn -> this.onClose()
        );
        this.addRenderableWidget(cancelButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int paperW = 250;
        int paperH = 150;
        int x = (this.width - paperW) / 2;
        int y = (this.height - paperH) / 2;

        guiGraphics.fill(x - 4, y - 4, x + paperW + 4, y + paperH + 4, 0x80000000);
        guiGraphics.fill(x, y, x + paperW, y + paperH, 0xFFE9DCB9);

        int borderColor = 0xFF6B5842;
        guiGraphics.fill(x, y, x + paperW, y + 2, borderColor);
        guiGraphics.fill(x, y + paperH - 2, x + paperW, y + paperH, borderColor);
        guiGraphics.fill(x, y, x + 2, y + paperH, borderColor);
        guiGraphics.fill(x + paperW - 2, y, x + paperW, y + paperH, borderColor);

        guiGraphics.drawString(this.font, "Введите имя блока:", x + 20, y + 20, 0xFF111111, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return nameEditBox.keyPressed(keyCode, scanCode, modifiers) || nameEditBox.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return nameEditBox.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }
}