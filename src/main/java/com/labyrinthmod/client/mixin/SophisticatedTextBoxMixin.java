package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.TextBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TextBox.class, remap = false)
public abstract class SophisticatedTextBoxMixin {
    @Inject(method = "renderBg", at = @At("HEAD"))
    private void otbor$paperTextField(GuiGraphics gfx, Minecraft mc, int mouseX, int mouseY, CallbackInfo ci) {
        if (mc.screen == null || !mc.screen.getClass().getName().contains("sophisticated")) return;
        TextBox box = (TextBox) (Object) this;
        int x = box.getX();
        int y = box.getY();
        int w = box.getWidth();
        int h = box.getHeight();
        gfx.fill(x - 2, y - 2, x + w + 2, y + h + 2, PaperRender.PAPER_LIGHT);
        gfx.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, PaperRender.INK_FADED);
    }
}
