package com.labyrinthmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

// Бумажная кнопка (нужна для экрана ускорения времени)
public class PaperButton extends Button {

    public PaperButton(int x, int y, int width, int height, Component message, Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused();

        // Фон
        g.fill(x, y, x + w, y + h, hovered ? 0xFFD6C3A6 : 0xFFC9B598);

        if (hovered) {
            // При наведении — красная двойная рамка
            g.renderOutline(x, y, w, h, 0xFF8B1E1E);
            g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF8B1E1E);
        } else {
            // Обычно — тёмная рамка + тень
            g.renderOutline(x, y, w, h, 0xFF3B3226);
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, 0xFF8A7A62);
        }

        // Светлая линия сверху
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0xFFDCCBAF);

        // Тонкий текст без тени
        Minecraft mc = Minecraft.getInstance();
        String text = getMessage().getString();
        g.drawString(mc.font, text, x + (w - mc.font.width(text)) / 2, y + (h - 8) / 2,
                hovered ? 0xFF8B1E1E : 0xFF1E1B16, false);
    }
}