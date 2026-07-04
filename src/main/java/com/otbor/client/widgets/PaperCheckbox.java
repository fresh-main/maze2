package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class PaperCheckbox extends AbstractButton {

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public PaperCheckbox(int x, int y, int w, int h, String label,
                         BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, w, h, Component.literal(label));
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress() {
        setter.accept(!getter.getAsBoolean());
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean checked = getter.getAsBoolean();

        int bx = x;
        int by = y + (getHeight() - 14) / 2;
        gfx.fill(bx, by, bx + 14, by + 14, PaperRender.PAPER_LIGHT);
        gfx.fill(bx, by, bx + 14, by + 1, PaperRender.INK);
        gfx.fill(bx, by + 13, bx + 14, by + 14, PaperRender.INK);
        gfx.fill(bx, by, bx + 1, by + 14, PaperRender.INK);
        gfx.fill(bx + 13, by, bx + 14, by + 14, PaperRender.INK);

        if (checked) {
            for (int i = 0; i < 4; i++) {
                gfx.fill(bx + 3 + i, by + 7 + i, bx + 4 + i, by + 8 + i, PaperRender.INK_RED);
            }
            for (int i = 0; i < 6; i++) {
                gfx.fill(bx + 6 + i, by + 10 - i, bx + 7 + i, by + 11 - i, PaperRender.INK_RED);
            }
        }

        var font = Minecraft.getInstance().font;
        gfx.drawString(font, getMessage(), x + 20, y + (getHeight() - 8) / 2 + 1,
                this.isHovered() ? PaperRender.INK_RED : PaperRender.INK, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput n) {
        this.defaultButtonNarrationText(n);
    }
}
