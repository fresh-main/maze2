package com.labyrinthmod.client.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ограничивает auto-pickup пикапы только хотбаром (slots 0-8) и offhand (slot 40).
 *
 * ВАЖНО: НЕ используем {@code @Shadow} на {@code Inventory.getItem(int)} —
 * этот метод приходит от интерфейса {@code Container}, и reobf-процесс не
 * переименовывает его в SRG-имя ({@code m_8020_}) внутри байт-кода mixin'а.
 * Из-за этого на сервере/клиенте mixin падал с
 * {@code @Shadow method getItem ... was not located in the target class}.
 * Доступ к offhand идёт напрямую через shadowed-поле {@code offhand}, а
 * {@code hasRemainingSpaceForItem} продублирован inline (логика тривиальная).
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow @Final public NonNullList<ItemStack> offhand;
    @Shadow @Final public Player player;
    @Shadow public int selected;

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void otbor$onlyHotbarFree(CallbackInfoReturnable<Integer> cir) {
        if (this.player != null && this.player.isCreative()) return;
        for (int i = 0; i < 9; i++) {
            if (this.items.get(i).isEmpty()) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void otbor$onlyHotbarStack(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        if (this.player != null && this.player.isCreative()) return;

        // Selected hotbar slot (0..8 → items[selected]).
        if (otbor$canStack(this.items.get(this.selected), stack)) {
            cir.setReturnValue(this.selected);
            return;
        }
        // Offhand slot — у Inventory это отдельный NonNullList offhand[0].
        // Vanilla getItem(40) возвращает offhand.get(0). Делаем то же напрямую.
        if (!this.offhand.isEmpty() && otbor$canStack(this.offhand.get(0), stack)) {
            cir.setReturnValue(40);
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (otbor$canStack(this.items.get(i), stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    /** Inline-копия {@code Inventory.hasRemainingSpaceForItem}. Не используем @Shadow
     *  на оригинальный protected метод, чтобы избежать reobf-проблем. */
    private static boolean otbor$canStack(ItemStack existing, ItemStack adding) {
        if (existing.isEmpty()) return false;
        if (!ItemStack.isSameItemSameTags(existing, adding)) return false;
        if (!existing.isStackable()) return false;
        if (existing.getCount() >= existing.getMaxStackSize()) return false;
        return true;
    }
}
