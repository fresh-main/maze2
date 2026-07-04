package com.labyrinthmod.client.mixin;

import com.labyrinthmod.LabyrinthMod;
import com.otbor.inventory.LockedSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Блокирует 27 ячеек рюкзака игрока в меню sophisticatedbackpacks.
 *
 * КЛЮЧЕВОЙ НЮАНС: StorageContainerMenuBase переопределяет getSlot(int), возвращая Slot из
 * отдельного списка realInventorySlots (а не из vanilla menu.slots). Screen'овский
 * updatePlayerSlotsPositions() через getSlot() писал x/y в оригинальные слоты из realInventorySlots,
 * а наши LockedSlot в menu.slots оставались с (0,0). Поэтому заменяем слоты в ОБОИХ списках.
 */
@Mixin(value = StorageContainerMenuBase.class, remap = false)
public abstract class StorageContainerMenuBaseMixin extends AbstractContainerMenu {

    private static final int HIDDEN = -9999;

    protected StorageContainerMenuBaseMixin() {
        super(null, 0);
    }

    @Inject(method = "addPlayerInventorySlots", at = @At("TAIL"))
    private void otbor$lockPlayerInv(Inventory inv, int numRows, boolean lockFirstHotbar, CallbackInfo ci) {
        int size = this.slots.size();
        // Прячем 27 основных ячеек игрока ЗА ЭКРАН (как и в AbstractContainerMenuMixin для
        // ванильных контейнеров). Хотбар (последние 9) оставляем видимым и интерактивным.
        // Это убирает «гранд» стандартного инвентаря под рюкзаком, который выглядел дико
        // вне paper-стилистики.
        int lockStart = size - 36;
        int lockEnd = size - 9;
        if (lockStart < 0) return;

        StorageContainerMenuBase<?> self = (StorageContainerMenuBase<?>) (Object) this;

        for (int i = lockStart; i < lockEnd; i++) {
            Slot orig = this.slots.get(i);
            if (orig == null) continue;
            LockedSlot locked = new LockedSlot(inv, orig.getContainerSlot(), HIDDEN, HIDDEN, inv.player);
            locked.index = i;
            this.slots.set(i, locked);
            if (i < self.realInventorySlots.size()) {
                self.realInventorySlots.set(i, locked);
            }
        }
        LabyrinthMod.LOGGER.info("[otbor] hidden {} backpack player slots (menu indices {}..{})",
                lockEnd - lockStart, lockStart, lockEnd - 1);
    }
}
