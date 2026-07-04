package com.labyrinthmod.client.mixin;

import com.otbor.inventory.LockedSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Скрывает основные 27 ячеек инвентаря игрока (Inventory.items[9..35]) во ВСЕХ контейнер-меню,
 * кроме E-инвентаря (там своё специальное поведение в InventoryMenuMixin).
 * Хотбар (0..8) и оффхенд/броня не трогаем.
 *
 * Слоты остаются в menu.slots под теми же индексами, чтобы shift-click и логика контейнера
 * работали корректно — мы лишь делаем их LockedSlot (нельзя класть/брать) с координатами
 * за пределами экрана.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    private static final int HIDDEN = -9999;

    @ModifyVariable(method = "addSlot", at = @At("HEAD"), argsOnly = true)
    private Slot otbor$hidePlayerMain(Slot slot) {
        if ((Object) this instanceof InventoryMenu) return slot;
        if (slot instanceof LockedSlot) return slot;
        if (!(slot.container instanceof Inventory inv)) return slot;
        int containerIdx = slot.getContainerSlot();
        if (containerIdx < 9 || containerIdx > 35) return slot;
        return new LockedSlot(inv, containerIdx, HIDDEN, HIDDEN, inv.player);
    }
}
