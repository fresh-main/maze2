package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.WidgetBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Button.class, remap = false)
public abstract class SophisticatedButtonMixin {
    @Shadow private boolean hovered;

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void otbor$paperButton(GuiGraphics gfx, Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (mc.screen == null || !mc.screen.getClass().getName().contains("sophisticated")) return;
        WidgetBase button = (WidgetBase) (Object) this;
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();
        hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        PaperRender.drawPaperCard(gfx, x, y, w, h, 1f,
                hovered ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE);
        int border = hovered ? PaperRender.INK_RED : PaperRender.INK;
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);
        ci.cancel();
    }
}
