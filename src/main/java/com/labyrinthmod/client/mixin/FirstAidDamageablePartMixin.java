package com.labyrinthmod.client.mixin;

import com.infection.capability.InfectionQuery;
import ichttt.mods.firstaid.common.damagesystem.DamageablePart;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Миксин в единую точку лечения First Aid. Работает на обеих сторонах:
 *   - на сервере читает уровень из capability
 *   - на клиенте — из ClientInfectionCache (блокирует локальную предиктивную
 *     отрисовку хила, чтобы HP у игрока не подпрыгивало визуально).
 */
@Mixin(value = DamageablePart.class, remap = false)
public class FirstAidDamageablePartMixin {

    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void infection$blockHealAtTerminal(float amount, Player player, boolean flag,
                                                CallbackInfoReturnable<Float> cir) {
        if (player == null) return;
        if (InfectionQuery.getLevel(player) >= 100) {
            cir.setReturnValue(0f);
        }
    }
}
