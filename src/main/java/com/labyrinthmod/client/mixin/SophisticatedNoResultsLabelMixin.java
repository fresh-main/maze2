package com.labyrinthmod.client.mixin;

import com.labyrinthmod.client.NoResultsLabelOwner;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Label;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Label.class, remap = false)
public abstract class SophisticatedNoResultsLabelMixin {
    @Shadow @Final private Component labelText;

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void otbor$wrapNoResults(GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof StorageScreenBase<?> screen)
                || ((NoResultsLabelOwner) screen).otbor$getNoResultsLabel() != (Object) this) return;

        Font font = mc.font;
        int maxWidth = screen.getXSize() - 20;
        int centerX = screen.getGuiLeft() + screen.getXSize() / 2;
        int y = ((Label) (Object) this).getY();
        for (var line : font.split(labelText, maxWidth)) {
            gfx.drawString(font, line, centerX - font.width(line) / 2, y, PaperRender.INK, false);
            y += font.lineHeight + 2;
        }
        ci.cancel();
    }
}
