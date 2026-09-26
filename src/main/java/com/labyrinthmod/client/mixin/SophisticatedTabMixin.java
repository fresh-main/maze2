package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.p3pp3rf1y.sophisticatedcore.client.gui.Tab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Tab.class, remap = false)
public abstract class SophisticatedTabMixin {
    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void otbor$paperTab(GuiGraphics gfx, Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (mc.screen == null || !mc.screen.getClass().getName().contains("sophisticated")) return;
        WidgetBase tab = (WidgetBase) (Object) this;
        int x = tab.getX();
        int y = tab.getY();
        int w = tab.getWidth();
        int h = tab.getHeight();
        if (w < 1 || h < 1) return;
        PaperRender.drawPaperCard(gfx, x, y, w, h, 1f, PaperRender.PAPER_BASE);
        gfx.fill(x, y, x + w, y + 1, PaperRender.INK);
        gfx.fill(x, y + h - 1, x + w, y + h, PaperRender.INK);
        gfx.fill(x, y, x + 1, y + h, PaperRender.INK);
        gfx.fill(x + w - 1, y, x + w, y + h, PaperRender.INK);
        ci.cancel();
    }
}
