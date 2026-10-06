package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Бумажный переключатель «показывать только доступные рецепты». */
@Mixin(StateSwitchingButton.class)
public abstract class RecipeFilterButtonMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void otbor$paperRecipeFilter(GuiGraphics gfx, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        StateSwitchingButton button = (StateSwitchingButton) (Object) this;
        if (button instanceof RecipeBookTabButton) return;

        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractFurnaceScreen)
                && !(screen instanceof CraftingScreen)
                && (screen == null || !screen.getClass().getName().contains("farmersdelight"))) {
            return;
        }

        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        PaperRender.drawPaperCard(gfx, x, y, w, h, 1f,
                hover ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE);

        int ink = button.isStateTriggered() ? 0xFF476B42 : PaperRender.INK_FADED;
        int cx = x + w / 2;
        int cy = y + h / 2;
        gfx.fill(cx - 6, cy - 5, cx + 5, cy - 4, ink);
        gfx.fill(cx - 6, cy - 1, cx + 3, cy, ink);
        gfx.fill(cx - 6, cy + 3, cx + 1, cy + 4, ink);
        if (button.isStateTriggered()) {
            gfx.fill(cx + 1, cy + 1, cx + 3, cy + 4, ink);
            gfx.fill(cx + 3, cy - 2, cx + 5, cy + 3, ink);
        }
        ci.cancel();
    }
}
