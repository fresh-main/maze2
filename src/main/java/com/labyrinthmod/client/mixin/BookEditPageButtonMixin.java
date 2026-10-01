package com.labyrinthmod.client.mixin;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PageButton.class)
public abstract class BookEditPageButtonMixin {
    @Shadow @Final private boolean isForward;

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void labyrinthmod$renderPaperArrow(GuiGraphics graphics, int mouseX, int mouseY,
                                                float partialTick, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof BookEditScreen)
                && !(minecraft.screen instanceof BookViewScreen)) return;

        PageButton button = (PageButton) (Object) this;
        float brightness = button.isHoveredOrFocused() ? 1.08F : 0.94F;
        PaperRender.drawPaperCard(graphics, button.getX(), button.getY(), button.getWidth(), button.getHeight(),
                brightness, PaperRender.PAPER_BASE);
        if (button.isHoveredOrFocused()) {
            graphics.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), PaperRender.INK_RED);
        }
        String arrow = isForward ? ">" : "<";
        graphics.drawCenteredString(minecraft.font, arrow,
                button.getX() + button.getWidth() / 2, button.getY() + 2, PaperRender.INK);
        ci.cancel();
    }
}
