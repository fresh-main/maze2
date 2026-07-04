package com.labyrinthmod.client.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(KeyboardHandler.class)
public class MazeKeyboardHandlerMixin {

    // Код клавиши F3 = 292
    private static final int KEY_F3 = 292;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        // Блокируем F3 для ВСЕХ игроков (включая операторов)
        if (key == KEY_F3) {
            ci.cancel();
        }
    }
}