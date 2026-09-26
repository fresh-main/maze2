package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import com.simibubi.create.content.equipment.toolbox.ToolboxScreen;
import com.simibubi.create.content.schematics.cannon.SchematicannonScreen;
import com.simibubi.create.foundation.gui.widget.Indicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Indicator.class)
public abstract class CreateIndicatorMixin {
    @Shadow(remap = false) public Indicator.State state;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void otbor$paperIndicator(GuiGraphics gfx, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof SchematicannonScreen) && !(screen instanceof ToolboxScreen)) return;
        Indicator indicator = (Indicator) (Object) this;
        if (!indicator.visible) return;
        int x = indicator.getX();
        int y = indicator.getY();
        int w = indicator.getWidth();
        int h = indicator.getHeight();
        PaperRender.drawPaperCard(gfx, x, y, w, h, 1f, PaperRender.PAPER_BASE);
        int ink = switch (state) {
            case GREEN -> 0xFF476B42;
            case YELLOW -> 0xFFA88850;
            case RED -> PaperRender.INK_RED;
            case ON -> PaperRender.INK_SOFT;
            case OFF -> PaperRender.INK_FADED;
        };
        gfx.fill(x + 3, y + 3, x + w - 3, y + h - 3, ink);
        ci.cancel();
    }
}
