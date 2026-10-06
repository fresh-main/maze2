package com.labyrinthmod.client.mixin;

import com.labyrinthmod.client.NoResultsLabelOwner;
import com.otbor.client.widgets.PaperContainerRender;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Label;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.TextBox;
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
        ci.cancel();
    }

    @Inject(method = "drawUpgradeBackground", at = @At("HEAD"), cancellable = true)
    private void otbor$drawPaperUpgradeSlots(GuiGraphics gfx, CallbackInfo ci) {
        StorageScreenBase<?> self = (StorageScreenBase<?>) (Object) this;
        var upgradeSlots = self.getMenu().upgradeSlots;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Slot slot : upgradeSlots) {
            if (slot.x <= -2000 || slot.y <= -2000) continue;
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
            maxX = Math.max(maxX, slot.x + 16);
            maxY = Math.max(maxY, slot.y + 16);
        }

        if (minX != Integer.MAX_VALUE) {
            int left = self.getGuiLeft();
            int top = self.getGuiTop();
            int padding = 5;
            PaperRender.drawPaperCard(gfx,
                    left + minX - padding, top + minY - padding,
                    maxX - minX + padding * 2, maxY - minY + padding * 2,
                    1.0f, PaperRender.PAPER_BASE);

            for (Slot slot : upgradeSlots) {
                if (slot.x <= -2000 || slot.y <= -2000) continue;
                PaperContainerRender.sketchSlotBox(gfx, left + slot.x, top + slot.y, 1.0f);
            }
        }

        // Contents are rendered afterwards; only the gray/dashed background is replaced.
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
