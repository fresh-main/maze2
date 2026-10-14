package com.labyrinthmod.client.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class TitleScreenMusicMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void labyrinthmod$useGlobalMusic(CallbackInfo ci) {
        // Музыкальный плеер обновляется через гарантированный Forge ClientTickEvent.
        // Здесь только блокируем ванильный планировщик, чтобы треки не накладывались.
        ci.cancel();
    }
}
