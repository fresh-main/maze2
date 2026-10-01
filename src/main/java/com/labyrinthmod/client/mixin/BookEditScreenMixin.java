package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin {

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/BookEditScreen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;)V")
    )
    private void labyrinthmod$drawBoard(BookEditScreen screen, GuiGraphics graphics) {
        PaperRender.drawBoardBackground(graphics, screen.width, screen.height);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V")
    )
    private void labyrinthmod$drawPaper(GuiGraphics graphics, ResourceLocation texture,
                                         int x, int y, int u, int v, int width, int height) {
        int paperHeight = Math.min(height + 28, graphics.guiHeight() - y - 4);
        PaperRender.drawPaper(graphics, x, y, width, paperHeight, 1.0F, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(graphics, x + 12, y + 10, false);
        PaperRender.drawPin(graphics, x + width - 12, y + 10, true);
        PaperRender.drawHandDivider(graphics, x + 18, y + 29, width - 36, PaperRender.INK_FADED);
    }
}
