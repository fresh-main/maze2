package com.labyrinthmod.client.mixin;

import com.otbor.client.LoadingScreenStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Полностью замещает {@code render()} у {@link GenericDirtMessageScreen} (экран
 * «Сохранение мира…»/«Загрузка…», который выводится при выходе из мира).
 *
 * Почему не общий {@link LoadingScreenBackgroundMixin}:
 * GenericDirtMessageScreen.render() напрямую вызывает {@code renderDirtBackground}
 * (а не {@code renderBackground}), плюс рисует ванильный белый title
 * {@code drawCenteredString} поверх. Наш generic-mixin на {@code renderBackground}
 * для него не срабатывает, и оставлять ванильный title поверх нашей бумаги нельзя —
 * визуально получается каша. Поэтому здесь {@code render} вырубается целиком и
 * рисуется только {@link LoadingScreenStyling#drawPaperBackdrop}.
 *
 * Виджетов у GenericDirtMessageScreen нет, тиков ему тоже не нужно — экран живёт
 * 1-2 кадра до переключения, так что cancel() безопасен.
 */
@Mixin(GenericDirtMessageScreen.class)
public abstract class GenericDirtMessageScreenMixin extends Screen {

    protected GenericDirtMessageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void otbor$paperRender(GuiGraphics gfx, int mx, int my, float pt, CallbackInfo ci) {
        LoadingScreenStyling.drawPaperBackdrop(gfx, this);
        ci.cancel();
    }
}
