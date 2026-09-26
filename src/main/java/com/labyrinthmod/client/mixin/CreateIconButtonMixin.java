package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import com.simibubi.create.content.equipment.toolbox.ToolboxScreen;
import com.simibubi.create.content.schematics.cannon.SchematicannonScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IconButton.class, remap = false)
public abstract class CreateIconButtonMixin {
    @Inject(method = "drawBg", at = @At("HEAD"), cancellable = true)
    private void otbor$paperControls(GuiGraphics gfx, AllGuiTextures texture, CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof SchematicannonScreen) && !(screen instanceof ToolboxScreen)) return;
        IconButton button = (IconButton) (Object) this;
        int x = button.getX();
        int y = button.getY();
        int w = button.getWidth();
        int h = button.getHeight();
        PaperRender.drawPaperCard(gfx, x, y, w, h, 1f,
                button.isHoveredOrFocused() ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE);
        gfx.fill(x + 2, y + 2, x + w - 2, y + h - 2,
                button.active ? PaperRender.INK_SOFT : PaperRender.INK_FADED);
        gfx.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, PaperRender.INK_RED);
        ci.cancel();
    }
}
