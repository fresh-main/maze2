package com.labyrinthmod.client;

import com.labyrinthmod.common.event.TimeAccelHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TimeAccelScreen extends Screen {

    public TimeAccelScreen() {
        super(Component.literal("УПРАВЛЕНИЕ ВРЕМЕНЕМ"));
    }

    @Override
    protected void init() {
        int bw = 70;
        int gap = 8;
        int centerX = this.width / 2;

        // Ряд УСКОРЕНИЯ
        double[] fast = {2, 4, 8, 16};
        int x = centerX - (fast.length * bw + (fast.length - 1) * gap) / 2;
        int y = this.height / 2 - 40;
        for (double s : fast) {
            addRenderableWidget(new PaperButton(x, y, bw, 20,
                    Component.literal("x" + (int) s),
                    btn -> { TimeAccelHandler.setSpeed(s); onClose(); }));
            x += bw + gap;
        }

        // Ряд ЗАМЕДЛЕНИЯ
        double[] slow = {0.5, 0.25, 0.125};
        x = centerX - (slow.length * bw + (slow.length - 1) * gap) / 2;
        y += 28;
        for (double s : slow) {
            String label = s == 0.5 ? "x1/2" : (s == 0.25 ? "x1/4" : "x1/8");
            addRenderableWidget(new PaperButton(x, y, bw, 20,
                    Component.literal(label),
                    btn -> { TimeAccelHandler.setSpeed(s); onClose(); }));
            x += bw + gap;
        }

        // КНОПКА СБРОСА
        addRenderableWidget(new PaperButton(centerX - 60, y + 34, 120, 20,
                Component.literal("СБРОС"),
                btn -> { TimeAccelHandler.setSpeed(1.0); onClose(); }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, "УПРАВЛЕНИЕ ВРЕМЕНЕМ", width / 2, height / 2 - 78, 0xFF8B1E1E);
        g.drawCenteredString(font, TimeAccelHandler.formatStatus(), width / 2, height / 2 - 60, 0xFF4A1F1F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}