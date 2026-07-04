package com.labyrinthmod.client.mixin;

import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StorageScreenBase.class, remap = false)
public abstract class StorageScreenBaseMixin {

    private static final int HOTBAR_TOP_OFFSET = 4;

    @Inject(method = "updateDimensionsAndSlotPositions", at = @At("TAIL"))
    private void otbor$compactPlayerInvArea(int height, CallbackInfo ci) {
        StorageScreenBase<?> self = (StorageScreenBase<?>) (Object) this;

        int storageRows = self.getMenu().getNumberOfRows();
        int storageEndY = 18 + storageRows * 18;
        int hotbarY = storageEndY + HOTBAR_TOP_OFFSET;

        int totalSlots = self.getMenu().getInventorySlotsSize();
        int hotbarStartIndex = totalSlots - 9;
        for (int i = 0; i < 9; i++) {
            int slotIndex = hotbarStartIndex + i;
            if (slotIndex < 0 || slotIndex >= totalSlots) continue;
            Slot slot = self.getMenu().getSlot(slotIndex);
            if (slot != null) {
                ((SlotAccessor) slot).setY(hotbarY);
            }
        }
    }
}