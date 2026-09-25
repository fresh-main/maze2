package com.labyrinthmod.client.mixin;

import com.otbor.client.ClientEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
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
    @Shadow private EditBox searchBox;

    @Inject(method = "init", at = @At("TAIL"))
    private void otbor$styleSearchField(CallbackInfo ci) {
        if (searchBox != null) {
            searchBox.setTextColor(0x352b22);
            searchBox.setTextColorUneditable(0x6c5744);
        }
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void otbor$hideVanillaTabTitle(GuiGraphics gfx, int mouseX, int mouseY, CallbackInfo ci) {
        // Vanilla puts every tab title at (8, 6), directly over the paper header/search row.
        // На вкладке инвентаря renderLabels также рисует крестик удаления. После
        // отмены ванильного заголовка возвращаем этот значок вручную в том же слоте.
        if (selectedTab != null && selectedTab.getType() == CreativeModeTab.Type.INVENTORY) {
            int x = 177;
            int y = 116;
            int color = 0xFF35261C;
            // Пиксельный крест 5×5, увеличенный до 10×10 пикселей.
            for (int row = 0; row < 5; row++) {
                int left = row <= 2 ? row : 4 - row;
                int right = 4 - left;
                gfx.fill(x + left * 2, y + row * 2, x + left * 2 + 2, y + row * 2 + 2, color);
                if (right != left) {
                    gfx.fill(x + right * 2, y + row * 2, x + right * 2 + 2, y + row * 2 + 2, color);
                }
            }
        }
        ci.cancel();
    }

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
