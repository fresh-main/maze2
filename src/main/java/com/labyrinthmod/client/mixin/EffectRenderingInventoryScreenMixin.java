package com.labyrinthmod.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Глушит ванильную панель эффектов сбоку от инвентаря: свой листок «АНОМАЛИИ · СТАТУС»
 * рисует {@link com.otbor.client.EffectsNote}, и сдвиг leftPos под ванильные баффы нам не нужен.
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class EffectRenderingInventoryScreenMixin {

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void otbor$skipVanillaEffects(GuiGraphics gfx, int mx, int my, CallbackInfo ci) {
        ci.cancel();
    }
}
