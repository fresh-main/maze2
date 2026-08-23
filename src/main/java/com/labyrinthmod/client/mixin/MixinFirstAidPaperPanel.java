package com.labyrinthmod.client.mixin;

import com.labyrinthmod.client.PaperStyleDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public class MixinFirstAidPaperPanel {

    private static boolean isFirstAid() {
        Screen s = Minecraft.getInstance().screen;
        return s != null && s.getClass().getName().toLowerCase().contains("firstaid");
    }

    // Убираем светло-серые fill
    @Inject(method = "fill(IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void removeGray(int minX, int minY, int maxX, int maxY, int color, CallbackInfo ci) {
        if (!isFirstAid()) return;
        PaperStyleDebug.fillCalls++;

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (r == g && g == b && r >= 0x80) {
            ci.cancel();
        }
    }

    // Убираем тёмный градиент
    @Inject(method = "fillGradient(IIIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void removeDark(int minX, int minY, int maxX, int maxY, int color1, int color2, CallbackInfo ci) {
        if (!isFirstAid()) return;
        PaperStyleDebug.gradCalls++;
        ci.cancel();
    }

    // ===== ПЕРЕХВАТ БОЛЬШИХ BLIT (серый панель — текстура) =====

    // blit(tex, x, y, u, v, w, h)
    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void removeBigBlit1(ResourceLocation tex, int x, int y, int u, int v, int w, int h, CallbackInfo ci) {
        if (!isFirstAid()) return;
        PaperStyleDebug.blitCalls++;
        if (w > 200 && h > 100) ci.cancel();
    }

    // blit(tex, x, y, uF, vF, w, h, texW, texH)
    @Inject(method = "blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void removeBigBlit2(ResourceLocation tex, int x, int y, float u, float v, int w, int h, int tw, int th, CallbackInfo ci) {
        if (!isFirstAid()) return;
        PaperStyleDebug.blitCalls++;
        if (w > 200 && h > 100) ci.cancel();
    }
}