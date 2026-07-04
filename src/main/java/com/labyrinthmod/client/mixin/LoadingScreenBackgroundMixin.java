package com.labyrinthmod.client.mixin;

import com.otbor.client.LoadingScreenStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class LoadingScreenBackgroundMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void otbor$paperInsteadOfDirt(GuiGraphics gfx, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (LoadingScreenStyling.isLoadingScreen(self)) {
            LoadingScreenStyling.drawPaperBackdrop(gfx, self);
            ci.cancel();
        }
    }
}
