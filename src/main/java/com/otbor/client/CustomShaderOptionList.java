package com.otbor.client;

import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class CustomShaderOptionList extends ShaderPackOptionList {

    public CustomShaderOptionList(ShaderPackScreen screen, NavigationController navigation, ShaderPack pack, Minecraft client, int width, int height, int top, int bottom, int left, int right) {
        super(screen, navigation, pack, client, width, height, top, bottom, left, right);
        this.setRenderBackground(false);
        this.setRenderHeader(false, 0);
        this.x0 = left;
        this.x1 = right;
        this.y0 = top;
        this.y1 = bottom;
    }

    @Override
    protected void renderBackground(GuiGraphics guiGraphics) {
        // Empty - disable any background
    }

    @Override
    protected void renderHeader(GuiGraphics guiGraphics, int x, int y) {
        // Empty - disable header
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // ПОЛНОСТЬЮ ПЕРЕОПРЕДЕЛЯЕМ render, чтобы не вызывать super.render()
        int listLength = this.getItemCount();
        for (int i = 0; i < listLength; i++) {
            int entryY = this.getRowTop(i);
            int entryHeight = this.itemHeight;

            if (entryY + entryHeight >= this.y0 && entryY <= this.y1) {
                var entry = this.getEntry(i);
                if (entry != null) {
                    boolean hovered = mouseX >= this.getRowLeft() && mouseX < this.getRowLeft() + this.getRowWidth()
                            && mouseY >= entryY && mouseY < entryY + entryHeight;

                    entry.render(guiGraphics, i, entryY, this.getRowLeft(), this.getRowWidth(), entryHeight,
                            mouseX, mouseY, hovered, partialTick);
                }
            }
        }

        // Рисуем скроллбар
        if (this.getItemCount() > 0) {
            int scrollbarX = this.x1 - 6;
            int scrollbarWidth = 6;
            int totalHeight = this.getItemCount() * this.itemHeight;
            int visibleHeight = this.y1 - this.y0;

            if (totalHeight > visibleHeight) {
                int scrollbarHeight = Math.max(16, visibleHeight * visibleHeight / totalHeight);
                int scrollBarY = this.y0 + (int) (this.getScrollAmount() * (visibleHeight - scrollbarHeight) / (totalHeight - visibleHeight));
                guiGraphics.fill(scrollbarX, scrollBarY, scrollbarX + scrollbarWidth, scrollBarY + scrollbarHeight, 0xFF888888);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listWidth = this.x1 - this.x0;
        int shift = (this.width - listWidth) / 2;
        return super.mouseClicked(mouseX + shift, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        int listWidth = this.x1 - this.x0;
        int shift = (this.width - listWidth) / 2;
        return super.mouseDragged(mouseX + shift, mouseY, button, dragX, dragY);
    }

    @Override
    public int getRowLeft() {
        int centerX = (this.x0 + this.x1) / 2;
        return centerX - this.getRowWidth() / 2;
    }

    @Override
    public int getRowWidth() {
        return this.x1 - this.x0 - 6;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - 6;
    }
}