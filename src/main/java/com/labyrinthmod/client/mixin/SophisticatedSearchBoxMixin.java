package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.client.gui.SearchBox", remap = false)
public abstract class SophisticatedSearchBoxMixin {
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void otbor$paperSearchBackground(GuiGraphics gfx, Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (mc.screen == null || !mc.screen.getClass().getName().contains("sophisticated")) return;
        // SearchBox changes its width in renderBg; use the final coordinates here.
        WidgetBase box = (WidgetBase) (Object) this;
        int x = box.getX();
        int y = box.getY();
        int w = box.getWidth();
        int h = box.getHeight();
        gfx.fill(x, y, x + w, y + h, PaperRender.PAPER_LIGHT);
        gfx.fill(x, y + h - 1, x + w, y + h, PaperRender.INK_FADED);
    }
}
