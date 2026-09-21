package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {
    @Shadow private EditBox searchBox;

    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void otbor$styleSearch(CallbackInfo ci) {
        searchBox.setBordered(false);
        searchBox.setTextColor(PaperRender.INK & 0xFFFFFF);
        searchBox.setTextColorUneditable(PaperRender.INK_FADED & 0xFFFFFF);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"))
    private void otbor$paperRecipePanel(GuiGraphics gfx, ResourceLocation texture,
                                        int x, int y, int u, int v, int width, int height) {
        PaperRender.drawPaperCard(gfx, x, y, width, height, 1f, PaperRender.PAPER_BASE);
        gfx.fill(x + 24, y + 12, x + 106, y + 28, PaperRender.INK_FADED);
        gfx.fill(x + 25, y + 13, x + 105, y + 27, PaperRender.PAPER_LIGHT);
        gfx.fill(x + 8, y + 38, x + width - 8, y + 39, PaperRender.INK_RED);
    }
}
