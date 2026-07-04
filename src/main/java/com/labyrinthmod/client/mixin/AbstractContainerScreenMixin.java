package com.labyrinthmod.client.mixin;

import com.otbor.client.BackpackScreenOverlay;
import com.otbor.client.widgets.PaperContainerRender;
import com.otbor.client.widgets.PaperRender;
import com.otbor.inventory.LockedSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> extends Screen {

    protected AbstractContainerScreenMixin(Component title) { super(title); }

    @Shadow protected int imageWidth;
    @Shadow protected int imageHeight;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected int titleLabelY;
    @Shadow protected int inventoryLabelY;
    @Shadow @Final protected T menu;

    @Unique private boolean otbor$compactedLayout = false;
    @Unique private boolean otbor$soundPlayed = false;

    @Inject(method = "init", at = @At("TAIL"))
    private void otbor$initContainer(CallbackInfo ci) {
        PaperContainerRender.openedAtFor(this);

        if (!otbor$soundPlayed) {
            otbor$soundPlayed = true;
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getSoundManager() != null) {
                    PaperRender.playPageFlip(mc.getSoundManager());
                }
            } catch (Throwable ignored) {}
        }

        if ((Object) this instanceof InventoryScreen) return;

        this.titleLabelY = -9999;
        this.inventoryLabelY = -9999;

        AbstractContainerScreen<?> selfScreen = (AbstractContainerScreen<?>) (Object) this;
        if (BackpackScreenOverlay.isBackpackScreen(selfScreen)) {
            return;
        }

        if (otbor$compactedLayout) return;

        boolean hasHiddenMain = false;
        List<Slot> hotbar = new ArrayList<>();
        for (Slot s : menu.slots) {
            if (s instanceof LockedSlot && s.x <= -1000) {
                hasHiddenMain = true;
            } else if (s.container instanceof Inventory) {
                int idx = s.getContainerSlot();
                if (idx >= 0 && idx < 9) hotbar.add(s);
            }
        }
        if (!hasHiddenMain || hotbar.isEmpty()) return;

        int shift = 58;
        for (Slot s : hotbar) {
            ((SlotAccessor) s).setY(s.y - shift);
        }
        this.imageHeight -= shift;
        this.topPos = (this.height - this.imageHeight) / 2;
        otbor$compactedLayout = true;
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
                    shift = At.Shift.AFTER))
    private void otbor$paperOverlay(GuiGraphics gfx, int mx, int my, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof InventoryScreen) return;
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        if (BackpackScreenOverlay.isVanillaPapered(self)) return;
        if (BackpackScreenOverlay.isBackpackScreen(self)) return;
        long openedAt = PaperContainerRender.openedAtFor(this);
        PaperContainerRender.renderContainer(gfx, self,
                this.leftPos, this.topPos, this.imageWidth, this.imageHeight,
                openedAt);
    }
}