package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import com.simibubi.create.content.schematics.table.SchematicTableScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchematicTableScreen.class, remap = false)
public abstract class SchematicTableScreenMixin {

    @Inject(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V",
                    shift = At.Shift.AFTER
            )
    )
    private void otbor$paperTableBackground(GuiGraphics gfx, float partialTick,
                                             int mouseX, int mouseY, CallbackInfo ci) {
        SchematicTableScreen self = (SchematicTableScreen) (Object) this;
        PaperRender.drawPaperCard(gfx, self.getGuiLeft(), self.getGuiTop(),
                AllGuiTextures.SCHEMATIC_TABLE.getWidth(),
                AllGuiTextures.SCHEMATIC_TABLE.getHeight(),
                1.0f, PaperRender.PAPER_LIGHT);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void otbor$hideOriginalTitle(GuiGraphics gfx, float partialTick,
                                          int mouseX, int mouseY, CallbackInfo ci) {
        SchematicTableScreen self = (SchematicTableScreen) (Object) this;
        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        int width = AllGuiTextures.SCHEMATIC_TABLE.getWidth();
        gfx.fill(x + 3, y + 1, x + width - 3, y + 15, PaperRender.PAPER_LIGHT);
    }
}
