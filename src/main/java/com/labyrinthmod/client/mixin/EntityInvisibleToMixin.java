package com.labyrinthmod.client.mixin;

import com.infection.client.minievent.ClientMiniEventState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Когда админ запускает LOOMING / FLICKER_PRESENCE на жертву, сервер ставит
 * админу {@code Entity.setInvisible(true)} — все клиенты перестают его рендерить.
 *
 * Сервер шлёт {@code S2CMiniEventStatePacket} с состоянием ACTIVE ТОЛЬКО жертве,
 * другим игрокам ничего не приходит. На клиенте жертвы {@link ClientMiniEventState}
 * получает админа в active state с типом, у которого {@code hasSilhouette = true}.
 *
 * Этот mixin переопределяет {@link Entity#isInvisibleTo(Player)} ТОЛЬКО на клиенте
 * жертвы: если этот entity (админ) есть в active-сессии локального клиента — return
 * false (видимый). LivingEntityRenderer рендерит admin'а → {@code MiniEventBlackLayer}
 * натягивает чёрный силуэт.
 *
 * Другие клиенты не получили packet → у них нет admin в BY_ADMIN → mixin не срабатывает →
 * admin остаётся невидимым.
 */
@Mixin(Entity.class)
public abstract class EntityInvisibleToMixin {

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void infection$forceVisibleForLoomingTarget(Player viewer, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level() == null || !self.level().isClientSide) return;
        if (viewer == null) return;
        if (self == viewer) return;
        // На клиенте жертвы в BY_ADMIN записан admin → shouldRenderBlack=true → видим админа.
        if (ClientMiniEventState.shouldRenderBlack(self.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
