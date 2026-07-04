package com.labyrinthmod.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * {@link ContainerScreen} в vanilla — это экран для chest/barrel/shulker/dispenser/hopper.
 * Его {@code renderBg} делает ДВА blit'а:
 *   1. chest-секция (containerRows*18+17 пикс)
 *   2. зона player-inventory (96 пикс) — на ФИКСИРОВАННОМ Y, не зависит от imageHeight
 *
 * AbstractContainerMenuMixin прячет основные слоты игрока в -9999, а
 * AbstractContainerScreenMixin сжимает imageHeight на 58 px. Но второй blit
 * остаётся видимым ниже бумаги — раньше мы маскировали его расширением бумаги
 * на +58 px, что давало большое пустое пространство под хотбаром.
 *
 * Теперь просто отменяем второй blit через Redirect (ordinal=1) — бумага не нужно
 * расширять, она заканчивается ровно после хотбара.
 */
@Mixin(ContainerScreen.class)
public class ChestScreenMixin {

    @Redirect(method = "renderBg",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V",
                    ordinal = 1))
    private void otbor$skipPlayerInvBlit(GuiGraphics gfx, ResourceLocation tex,
                                          int x, int y, int u, int v, int w, int h) {
        // no-op — paper из BackpackScreenOverlay уже покрывает chest-секцию,
        // player-inv блит больше не нужен (мы спрятали слоты в -9999).
    }
}
