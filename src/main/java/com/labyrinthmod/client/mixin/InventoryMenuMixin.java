package com.labyrinthmod.client.mixin;

import com.otbor.inventory.LockedSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu {

    protected InventoryMenuMixin() {
        super(null, 0);
    }

    private static final int ARMOR_X   = 172;
    private static final int HELM_Y    = 24;
    private static final int CHEST_Y   = 54;
    private static final int LEGS_Y    = 84;
    private static final int BOOTS_Y   = 114;
    private static final int OFFH_Y    = 144;

    private static final int HOTBAR_X0 = 29;
    private static final int HOTBAR_Y  = 175;

    private static final int HIDDEN_X  = -9999;
    private static final int HIDDEN_Y  = -9999;

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/player/Inventory;ZLnet/minecraft/world/entity/player/Player;)V",
            at = @At("TAIL")
    )
    private void otbor$rebuildLayout(Inventory inventory, boolean localFlag, Player player, CallbackInfo ci) {
        for (int menuIndex = 0; menuIndex <= 4; menuIndex++) {
            Slot s = this.slots.get(menuIndex);
            ((SlotAccessor) s).setX(HIDDEN_X);
            ((SlotAccessor) s).setY(HIDDEN_Y);
        }

        int[] armorY = { HELM_Y, CHEST_Y, LEGS_Y, BOOTS_Y };
        for (int i = 0; i < 4; i++) {
            Slot s = this.slots.get(5 + i);
            ((SlotAccessor) s).setX(ARMOR_X);
            ((SlotAccessor) s).setY(armorY[i]);
        }

        for (int menuIndex = 9; menuIndex <= 35; menuIndex++) {
            Slot original = this.slots.get(menuIndex);
            LockedSlot locked = new LockedSlot(
                    inventory, original.getContainerSlot(),
                    HIDDEN_X, HIDDEN_Y,
                    player
            );
            locked.index = menuIndex;
            this.slots.set(menuIndex, locked);
        }

        for (int menuIndex = 36; menuIndex <= 44; menuIndex++) {
            Slot s = this.slots.get(menuIndex);
            ((SlotAccessor) s).setX(HOTBAR_X0 + (menuIndex - 36) * 18);
            ((SlotAccessor) s).setY(HOTBAR_Y);
        }

        if (this.slots.size() > 45) {
            Slot s = this.slots.get(45);
            ((SlotAccessor) s).setX(ARMOR_X);
            ((SlotAccessor) s).setY(OFFH_Y);
        }
    }
}