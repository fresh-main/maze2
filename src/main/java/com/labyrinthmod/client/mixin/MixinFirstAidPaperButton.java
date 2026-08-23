package com.labyrinthmod.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ВАЖНО: целимся в AbstractButton — там renderWidget с реальным кодом!
// (в AbstractWidget он абстрактный = краш NPE, как было в логе)
@Mixin(AbstractButton.class)
public class MixinFirstAidPaperButton {

    @Inject(method = "renderWidget(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void paperRender(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // Только в экране FirstAid
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getName().toLowerCase().contains("firstaid")) return;

        AbstractButton self = (AbstractButton) (Object) this;
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();
        boolean hovered = self.isHoveredOrFocused();

        // Фон: чуть светлее при наведении
        g.fill(x, y, x + w, y + h, hovered ? 0xFFDCCBAF : 0xFFC9B598);

        if (hovered) {
            // ПРИ НАВЕДЕНИИ: тёмно-красная двойная рамка (как кнопка "ИГРАТЬ")
            g.renderOutline(x, y, w, h, 0xFF8B1E1E);
            g.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xFF8B1E1E);
        } else {
            // Обычное состояние: тёмная рамка
            g.renderOutline(x, y, w, h, 0xFF3B3226);
            // Тень снизу
            g.fill(x + 2, y + h - 3, x + w - 2, y + h - 1, 0xFF8A7A62);
        }

        // Текст: тёмно-красный при наведении, тёмный обычно (без тени = тонкий)
        Minecraft mc = Minecraft.getInstance();
        String text = self.getMessage().getString();
        int textWidth = mc.font.width(text);
        g.drawString(
                mc.font,
                text,
                x + (w - textWidth) / 2,
                y + (h - 8) / 2,
                hovered ? 0xFF8B1E1E : 0xFF1E1B16,
                false
        );

        // Отменяем ванильную отрисовку кнопки
        ci.cancel();
    }
}