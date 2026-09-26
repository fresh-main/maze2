package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void otbor$paperRecipeTile(GuiGraphics gfx, ResourceLocation texture,
                                       int x, int y, int u, int v, int width, int height) {
        PaperRender.drawPaperCard(gfx, x, y, width, height, 1f, PaperRender.PAPER_LIGHT);
        gfx.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, PaperRender.INK_RED);
    }
}
