package com.labyrinthmod.client.mixin;

import com.otbor.client.LoadingScreenStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Полностью перехватывает рендер всех ванильных экранов загрузки,
 * чтобы отрисовывать вместо них кастомный "бумажный" интерфейс из LoadingScreenStyling.
 */
@Mixin(value = {
        LevelLoadingScreen.class,
        ProgressScreen.class,
        ReceivingLevelScreen.class,
        ConnectScreen.class,
        GenericDirtMessageScreen.class
}, priority = 1500)
public abstract class LoadingScreenMixin extends Screen {

    // Требуется защищённым конструктором Screen — никогда не вызывается,
    // но нужен для компиляции миксина.
    protected LoadingScreenMixin(net.minecraft.network.chat.Component title) {
        super(title);
        throw new IllegalStateException("Mixin constructor must not be called");
    }

    /**
     * Перехватываем самый верх рендера — до того, как ванильный код
     * нарисует свой прогресс-бар, текст "Loading terrain..." и т.п.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = true
    )
    private void labyrinthmod$replaceLoadingRender(
            GuiGraphics gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        Screen self = (Screen) (Object) this;

        // 1. Рисуем ванильный фон (тёмный оверлей), чтобы не было "дыры" до первого кадра.
        try {
            self.renderBackground(gfx);
        } catch (Throwable ignored) {
            // На случай, если renderBackground недоступен в конкретной версии
            gfx.fill(0, 0, self.width, self.height, 0xFF0A0A0A);
        }

        // 2. Рисуем кастомный "бумажный" экран с прогресс-баром, штампами и т.д.
        try {
            LoadingScreenStyling.drawPaperBackdrop(gfx, self);
        } catch (Throwable t) {
            // Если кастомный рендер упал — хотя бы не крашим игру
            com.mojang.logging.LogUtils.getLogger()
                    .warn("[labyrinthmod] custom loading screen render failed", t);
        }

        // 3. Отменяем ванильный рендер полностью — прогресс-бар, текст, виджеты.
        ci.cancel();
    }
}