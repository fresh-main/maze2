package com.otbor.client;

import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class CustomShaderPackList extends ShaderPackSelectionList {

    public CustomShaderPackList(ShaderPackScreen screen, Minecraft minecraft, int width, int height, int y0, int y1, int x0, int x1) {
        super(screen, minecraft, width, height, y0, y1, x0, x1);
        this.x0 = x0;
        this.x1 = x1;
        this.y0 = y0;
        this.y1 = y1;
        this.setRenderBackground(false);
        this.setRenderHeader(false, 0);
    }

    @Override
    protected void renderBackground(GuiGraphics guiGraphics) {
        // Empty - disable any background
    }

    // ИСПРАВЛЕНИЕ: Добавляем этот метод, чтобы экран не мог отрисовать стандартный заголовок сверху
    @Override
    protected void renderHeader(GuiGraphics guiGraphics, int x, int y) {
        // Пустой - полностью убираем старый заголовок (кнопка "назад" и название пака)
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

    // ИСПРАВЛЕНИЕ: Центрируем строки относительно нашего сдвинутого списка,
    // а не относительно всего экрана. Это починит кнопку вкл/выкл шейдеров.
    @Override
    public int getRowLeft() {
        int centerX = (this.x0 + this.x1) / 2;
        return centerX - this.getRowWidth() / 2;
    }

    @Override
    public int getRowWidth() {
        // Возвращаем оригинальную логику Iris, чтобы кнопка не растягивалась на всю ширину
        return Math.min(308, this.width - 50);
    }

    @Override
    protected int getScrollbarPosition() {
        return this.x1 - 6;
    }
}