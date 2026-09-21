package com.labyrinthmod.client.mixin;

import com.simibubi.create.content.equipment.toolbox.ToolboxScreen;
import com.simibubi.create.content.schematics.cannon.SchematicannonScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractSimiContainerScreen.class, remap = false)
public abstract class CreatePlayerInventoryBackgroundMixin {
    @Inject(method = "renderPlayerInventory", at = @At("HEAD"), cancellable = true)
    private void otbor$hotbarOnly(GuiGraphics gfx, int x, int y, CallbackInfo ci) {
        if ((Object) this instanceof ToolboxScreen || (Object) this instanceof SchematicannonScreen) {
            ci.cancel();
        }
    }
}
