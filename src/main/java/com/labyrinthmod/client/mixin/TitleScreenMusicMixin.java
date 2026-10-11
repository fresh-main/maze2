package com.labyrinthmod.client.mixin;

import com.otbor.client.OtborTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class TitleScreenMusicMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void labyrinthmod$useMenuMusic(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof OtborTitleScreen) {
            ci.cancel();
        }
    }
}
