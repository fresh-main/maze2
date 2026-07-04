package com.labyrinthmod.client.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Options;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Полностью блокирует F3 (debug overlay toggle).
 *
 * Раньше в {@code ClientEvents.onClientTick} мы сбрасывали
 * {@code mc.options.renderDebug = false} раз в тик. Между нажатием F3 и
 * следующим тиком (до 50мс) overlay успевал мигнуть на пару кадров — игроки
 * жаловались. Здесь перехватываем сам PUTFIELD на {@code renderDebug} прямо
 * внутри {@code KeyboardHandler.keyPress} и делаем no-op. Toggle от F3
 * вообще не происходит — overlay никогда не открывается.
 *
 * Other writes к {@code renderDebug} (наш собственный сброс в onClientTick)
 * не затрагиваются — Redirect ограничен методом keyPress.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Redirect(
            method = "keyPress(JIIII)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Options;renderDebug:Z",
                    opcode = Opcodes.PUTFIELD
            ),
            require = 0,
            allow = 4
    )
    private void otbor$blockF3DebugToggle(Options instance, boolean ignored) {
        // no-op: оставляем renderDebug как есть, F3 ничего не переключает.
    }
}
