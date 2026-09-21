package com.otbor.client.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.resources.ResourceLocation;

/** Recipe book toggle using the same paper and ink as the surrounding container. */
public final class PaperRecipeBookButton extends ImageButton {
    private final ImageButton original;

    public PaperRecipeBookButton(ImageButton original) {
        super(original.getX(), original.getY(), original.getWidth(), original.getHeight(),
                0, 0, ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/recipe_button.png"),
                button -> {});
        this.original = original;
    }

    @Override
    public void onPress() {
        original.onPress();
        setPosition(original.getX(), original.getY());
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager sounds) {
        PaperRender.playPageFlip(sounds);
    }

    @Override
    public void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int paper = isHoveredOrFocused() ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE;
        PaperRender.drawPaperCard(gfx, x, y, w, h, 1f, paper);

        int cx = x + w / 2;
        int cy = y + h / 2;
        gfx.fill(cx - 7, cy - 5, cx - 1, cy + 5, PaperRender.PAPER_LIGHT);
        gfx.fill(cx + 1, cy - 5, cx + 7, cy + 5, PaperRender.PAPER_LIGHT);
        gfx.fill(cx - 8, cy - 6, cx - 7, cy + 6, PaperRender.INK);
        gfx.fill(cx + 7, cy - 6, cx + 8, cy + 6, PaperRender.INK);
        gfx.fill(cx - 7, cy + 5, cx + 8, cy + 6, PaperRender.INK);
        gfx.fill(cx, cy - 5, cx + 1, cy + 6, PaperRender.INK);
        gfx.fill(cx - 5, cy - 2, cx - 2, cy - 1, PaperRender.INK_FADED);
        gfx.fill(cx + 3, cy - 2, cx + 6, cy - 1, PaperRender.INK_FADED);
        gfx.fill(cx - 5, cy + 1, cx - 2, cy + 2, PaperRender.INK_FADED);
        gfx.fill(cx + 3, cy + 1, cx + 6, cy + 2, PaperRender.INK_FADED);
        gfx.fill(cx + 5, cy - 6, cx + 7, cy - 2, PaperRender.INK_RED);
    }
}
