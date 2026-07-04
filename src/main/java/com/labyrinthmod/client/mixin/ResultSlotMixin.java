package com.labyrinthmod.client.mixin;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import com.labyrinthmod.common.capability.FractionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 1. Меняем цель на Slot, так как mayPickup находится именно там
@Mixin(Slot.class)
public class ResultSlotMixin {

    // 2. Убраны все лишние пробелы в "mayPickup" и "HEAD"
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void labyrinthmod$restrictPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        // Проверяем, что текущий слот является именно слотом результата крафта
        if (!((Object) this instanceof ResultSlot)) {
            return;
        }

        if (player.level().isClientSide) return;

        // Получаем предмет из слота
        ItemStack stack = ((Slot) (Object) this).getItem();
        if (stack.isEmpty()) return;

        String playerFraction = player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.hasFraction() ? data.getFraction().name() : "NONE")
                .orElse("NONE");

        // Проверка через ваш менеджер
        if (!CraftRestrictionManager.canCraft(stack.getItem(), playerFraction)) {
            // Возвращаем false, чтобы игрок не смог забрать предмет
            cir.setReturnValue(false);

        }
    }
}