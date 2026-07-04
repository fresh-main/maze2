package com.labyrinthmod.client.mixin;

import com.otbor.client.ClientEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.client.gui.CreativeTabsScreenPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    // В 1.20.1 selectedTab — это private static поле самого CreativeModeInventoryScreen
    @Shadow private static CreativeModeTab selectedTab;

    // currentPage — это private поле экземпляра
    @Shadow private CreativeTabsScreenPage currentPage;

    // Приватные методы для получения относительных координат таба (относительно leftPos и topPos)
    @Invoker("getTabX")
    abstract int invokeGetTabX(CreativeModeTab tab);

    @Invoker("getTabY")
    abstract int invokeGetTabY(CreativeModeTab tab);

    // В 1.20.1 метод называется renderTabButton и принимает только GuiGraphics и CreativeModeTab
    @Inject(method = "renderTabButton(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/CreativeModeTab;)V",
            at = @At("HEAD"), cancellable = true)
    private void otbor$cancelVanillaTabRender(GuiGraphics gfx, CreativeModeTab tab, CallbackInfo ci) {
        // Отменяем ванильную отрисовку (и фона, и иконки)
        ci.cancel();

        // Получаем относительные координаты
        int relX = this.invokeGetTabX(tab);
        int relY = this.invokeGetTabY(tab);

        boolean isSelected = (selectedTab == tab);

        // Передаём относительные координаты и сам таб в ClientEvents
        ClientEvents.addCreativeTabData(relX, relY, isSelected, tab);
    }
}