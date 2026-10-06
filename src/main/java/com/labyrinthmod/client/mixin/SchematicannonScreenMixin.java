package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import com.simibubi.create.content.schematics.cannon.SchematicannonScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Убирает большую серую панель инвентаря игрока под пушкой; хотбар рисуется отдельно. */
@Mixin(value = SchematicannonScreen.class, remap = false)
public abstract class SchematicannonScreenMixin {

    @Inject(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/schematics/cannon/SchematicannonScreen;renderPrintingProgress(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void otbor$paperSchematicannonBackground(GuiGraphics gfx, float partialTick,
                                                      int mouseX, int mouseY, CallbackInfo ci) {
        SchematicannonScreen self = (SchematicannonScreen) (Object) this;
        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        int width = AllGuiTextures.SCHEMATICANNON_TOP.getWidth();
        int topHeight = AllGuiTextures.SCHEMATICANNON_TOP.getHeight();
        int bottomHeight = AllGuiTextures.SCHEMATICANNON_BOTTOM.getHeight();

        PaperRender.drawPaperCard(gfx, x, y, width, topHeight + bottomHeight,
                1.0f, PaperRender.PAPER_LIGHT);

        // Hand-drawn process arrow; slots, status and progress are rendered by Create afterwards.
        int arrowY = y + 96;
        int arrowStart = x + 69;
        int arrowEnd = x + 116;
        gfx.fill(arrowStart, arrowY - 1, arrowEnd - 6, arrowY + 1, PaperRender.INK_FADED);
        gfx.fill(arrowEnd - 10, arrowY - 5, arrowEnd - 6, arrowY + 5, PaperRender.INK_FADED);
        gfx.fill(arrowEnd - 6, arrowY - 3, arrowEnd - 3, arrowY + 3, PaperRender.INK_FADED);
        gfx.fill(arrowEnd - 3, arrowY - 1, arrowEnd, arrowY + 1, PaperRender.INK_FADED);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void otbor$hideCreateTitleStrip(GuiGraphics gfx, float partialTick,
                                             int mouseX, int mouseY, CallbackInfo ci) {
        SchematicannonScreen self = (SchematicannonScreen) (Object) this;
        int x = self.getGuiLeft();
        int y = self.getGuiTop();
        int width = AllGuiTextures.SCHEMATICANNON_TOP.getWidth();
        gfx.fill(x + 3, y + 1, x + width - 3, y + 15, PaperRender.PAPER_LIGHT);
    }

    @Redirect(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/schematics/cannon/SchematicannonScreen;renderPlayerInventory(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            ),
            require = 0
    )
    private void otbor$hidePlayerInventoryPanel(SchematicannonScreen screen,
                                                 GuiGraphics gfx, int x, int y) {
        // Основные 27 слотов скрыты, а хотбар уже находится внутри бумажного листа.
    }
}
