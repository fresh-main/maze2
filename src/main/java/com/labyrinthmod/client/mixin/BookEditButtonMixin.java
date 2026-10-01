package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class BookEditButtonMixin {

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void labyrinthmod$renderPaperButton(GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof BookEditScreen)
                && !(minecraft.screen instanceof BookViewScreen)) return;

        AbstractButton button = (AbstractButton) (Object) this;
        if (button instanceof PageButton) return;

        int color = button.active ? PaperRender.PAPER_BASE : PaperRender.PAPER_DARK;
        float brightness = button.isHoveredOrFocused() ? 1.08F : 0.94F;
        PaperRender.drawPaperCard(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(), brightness, color);
        if (button.isHoveredOrFocused() && button.active) {
            graphics.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), PaperRender.INK_RED);
        }
        int textColor = button.active ? PaperRender.INK : PaperRender.INK_FADED;
        graphics.drawCenteredString(minecraft.font, button.getMessage(),
                button.getX() + button.getWidth() / 2,
                button.getY() + (button.getHeight() - 8) / 2, textColor);
        ci.cancel();
    }
}
