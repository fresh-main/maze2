package com.labyrinthmod.client.mixin;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(DebugScreenOverlay.class)
public class DebugOverlayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(CallbackInfo ci) {
        // Полностью отключаем рендер экрана отладки для всех
        ci.cancel();
    }
}