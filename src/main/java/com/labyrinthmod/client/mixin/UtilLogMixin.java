package com.labyrinthmod.client.mixin; // или ваш пакет

import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Util.class)
public class UtilLogMixin {

    // ★ ДОБАВЛЕНО: require = 0 делает инъекцию опциональной.
    // Если метод не найден — миксин просто пропускается без краша.
    @Inject(
            method = "logAndPauseIfInIde",
            at = @At("HEAD"),
            cancellable = true,
            require = 0  // ← ВОТ ЭТО КЛЮЧЕВОЕ ИЗМЕНЕНИЕ
    )
    private static void labyrinthmod$suppressFarChunkError(String message, CallbackInfo ci) {
        // Ваша логика подавления ошибки
        ci.cancel();
    }
}