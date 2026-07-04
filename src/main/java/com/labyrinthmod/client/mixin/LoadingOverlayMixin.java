package com.labyrinthmod.client.mixin;

import com.otbor.client.OtborGameLoadingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {

    @Unique
    private OtborGameLoadingScreen otbor$screen;

    @Unique
    private boolean otbor$initialized = false;

    /**
     * Перехватываем рендер ванильного LoadingOverlay и вместо него
     * рендерим наш OtborGameLoadingScreen.
     * tick() у LoadingOverlay нет — обновление происходит через render() каждый кадр.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void otbor$replaceRender(GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        // При первом рендере — создаём экран и инициализируем его
        if (!otbor$initialized) {
            otbor$screen = new OtborGameLoadingScreen();
            otbor$screen.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            otbor$initialized = true;
        }

        // Рендерим наш экран вместо ванильного
        otbor$screen.render(gfx, mouseX, mouseY, partialTick);

        // Отменяем ванильный рендер
        ci.cancel();
    }
}