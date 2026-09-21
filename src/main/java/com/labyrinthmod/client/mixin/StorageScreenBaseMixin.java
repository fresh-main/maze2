package com.labyrinthmod.client.mixin;

import com.labyrinthmod.client.NoResultsLabelOwner;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Inventory;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.TextBox;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Label;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StorageScreenBase.class, remap = false)
public abstract class StorageScreenBaseMixin implements NoResultsLabelOwner {

    private static final int HOTBAR_TOP_OFFSET = 4;
    @Shadow private TextBox searchBox;
    @Shadow private Label noResultsLabel;

    @Override
    public Label otbor$getNoResultsLabel() {
        return noResultsLabel;
    }

    @Inject(method = "addSearchBox", at = @At("TAIL"))
    private void otbor$styleBackpackSearch(CallbackInfo ci) {
        if (searchBox != null) {
            searchBox.setTextColor(0x352b22);
            searchBox.setTextColorUneditable(0x6c5744);
        }
    }

    @Inject(method = "drawInventoryBg", at = @At("HEAD"), cancellable = true)
    private void otbor$hideUnusedPlayerInventoryBackground(GuiGraphics gfx, int x, int y,
                                                               ResourceLocation texture, CallbackInfo ci) {
        // The original texture includes three player-inventory rows that are locked here.
        // The compact paper panel is drawn by BackpackScreenOverlay.
        ci.cancel();
    }

    @Inject(method = "updatePlayerSlotsPositions", at = @At("TAIL"))
    private void otbor$compactPlayerInvArea(CallbackInfo ci) {
        StorageScreenBase<?> self = (StorageScreenBase<?>) (Object) this;
        int storageRows = self.getMenu().getNumberOfRows();
        int storageEndY = 18 + storageRows * 18;
        int hotbarY = storageEndY + HOTBAR_TOP_OFFSET;
        otbor$positionPlayerSlots(self.getMenu().slots, hotbarY);
        otbor$positionPlayerSlots(self.getMenu().realInventorySlots, hotbarY);
    }

    private static void otbor$positionPlayerSlots(Iterable<Slot> slots, int hotbarY) {
        for (Slot slot : slots) {
            if (!(slot.container instanceof Inventory)) continue;
            int index = slot.getContainerSlot();
            if (index >= 0 && index < 9) {
                ((SlotAccessor) slot).setX(8 + index * 18);
                ((SlotAccessor) slot).setY(hotbarY);
            } else if (index >= 9 && index < 36) {
                ((SlotAccessor) slot).setX(-9999);
                ((SlotAccessor) slot).setY(-9999);
            }
        }
    }
}
