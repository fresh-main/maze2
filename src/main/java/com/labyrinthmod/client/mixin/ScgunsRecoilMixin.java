package com.labyrinthmod.client.mixin;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.client.handler.RecoilHandler;
import top.ribs.scguns.event.GunFireEvent;

@Mixin(value = RecoilHandler.class, remap = false)
public abstract class ScgunsRecoilMixin {
    @Shadow(remap = false) private float cameraRecoil;
    @Shadow(remap = false) private double gunRecoilAngle;
    @Shadow(remap = false) private double gunRecoilNormal;

    @Inject(method = "onGunFire", at = @At("TAIL"), remap = false)
    private void labyrinthmod$reduceSoldierRecoil(GunFireEvent.Post event, CallbackInfo ci) {
        if (!event.isClient()) return;
        boolean soldier = event.getEntity().getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.SOLDIER).orElse(false);
        if (!soldier) return;
        cameraRecoil *= 0.7f;
        gunRecoilAngle *= 0.7;
        gunRecoilNormal *= 0.7;
    }
}
