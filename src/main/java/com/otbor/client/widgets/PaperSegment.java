package com.otbor.client.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class PaperSegment extends AbstractButton {

    private final String[] options;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public PaperSegment(int x, int y, int w, int h, String[] options,
                         IntSupplier getter, IntConsumer setter) {
        super(x, y, w, h, Component.literal(""));
        this.options = options;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    public void onPress() {
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        PaperRender.playPageFlip(handler);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !this.visible) return false;
        if (button == 0 && this.clicked(mx, my)) {
            int x = getX();
            int w = getWidth();
            int each = Math.max(1, w / options.length);
            int i = Math.max(0, Math.min(options.length - 1, (int) ((mx - x) / each)));
            setter.accept(i);
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int selected = Math.max(0, Math.min(options.length - 1, getter.getAsInt()));
        int each = Math.max(1, w / options.length);

        var font = Minecraft.getInstance().font;

        for (int i = 0; i < options.length; i++) {
            int sx = x + i * each;
            boolean active = i == selected;
            boolean hover = isHovered() && mouseX >= sx && mouseX < sx + each - 2;

            int bg = active ? PaperRender.PAPER_LIGHT : (hover ? PaperRender.PAPER_BASE : 0x40000000);
            int border = active ? PaperRender.INK : PaperRender.INK_FADED;

            gfx.fill(sx, y, sx + each - 2, y + h, bg);
            gfx.fill(sx, y, sx + each - 2, y + 1, border);
            gfx.fill(sx, y + h - 1, sx + each - 2, y + h, border);
            gfx.fill(sx, y, sx + 1, y + h, border);
            gfx.fill(sx + each - 3, y, sx + each - 2, y + h, border);

            String label = options[i];
            int lw = font.width(label);
            gfx.drawString(font, label, sx + each / 2 - lw / 2 - 1, y + (h - 8) / 2,
                    active ? PaperRender.INK : PaperRender.INK_FADED, false);

            if (active) {
                gfx.fill(sx + 4, y + h - 3, sx + each - 6, y + h - 2, PaperRender.INK_RED);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput n) {
        this.defaultButtonNarrationText(n);
    }
}
