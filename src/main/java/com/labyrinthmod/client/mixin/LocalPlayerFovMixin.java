package com.labyrinthmod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Убираем зависимость FOV-множителя от MOVEMENT_SPEED игрока, чтобы любые ускорения
 * не «разъезжали» большие FOV вроде 110°. Метод getFieldOfViewModifier объявлен
 * на AbstractClientPlayer, не на LocalPlayer.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class LocalPlayerFovMixin {

    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void otbor$stableFov(CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Abilities abilities = self.getAbilities();

        float f = 1.0f;
        if (abilities.flying) {
            f *= 1.1f;
        }
        // ВНИМАНИЕ: множитель от MOVEMENT_SPEED НЕ применяем — это и есть «расползание»
        // экрана при ускорениях.
        if (self.isSprinting()) {
            f *= 1.15f;
        }
        if (self.isUsingItem()) {
            ItemStack using = self.getUseItem();
            if (using.is(Items.BOW)) {
                float draw = (float) self.getTicksUsingItem() / 20.0f;
                if (draw > 1.0f) draw = 1.0f;
                else draw = draw * draw;
                f *= 1.0f - draw * 0.15f;
            }
        }

        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        cir.setReturnValue(Mth.lerp(scale, 1.0f, f));
    }
}
