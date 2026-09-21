package com.labyrinthmod.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.client.gui.CookingPotScreen;

@Mixin(CookingPotScreen.class)
public abstract class CookingPotScreenMixin {
    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void otbor$hideOverlappingLabels(GuiGraphics gfx, int mouseX, int mouseY, CallbackInfo ci) {
        ci.cancel();
    }
}
