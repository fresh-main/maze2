package com.labyrinthmod.client.mixin;

import com.otbor.client.LoadingScreenStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ReceivingLevelScreen рисует собственный градиент + текст и не зовёт renderBackground.
 * Переинъектируемся на TAIL метода render, рисуя нашу бумагу ПОВЕРХ ванилы.
 */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void otbor$overlayPaper(GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        LoadingScreenStyling.drawPaperBackdrop(gfx, (ReceivingLevelScreen) (Object) this);
    }
}
