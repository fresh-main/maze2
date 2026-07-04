package com.labyrinthmod.client.mixin;

import com.infection.client.gui.InfectionNoteCard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Дорисовывает карточку «ЗАПИСКА» на InventoryScreen — прямо под инвентарём,
 * центрированно. Так она держится в общем кластере (АНОМАЛИИ слева, ЛИЧНОЕ ДЕЛО
 * справа, инвентарь в центре, ЗАПИСКА снизу), а не уезжает за нижний край влево.
 *
 * Y-позиция уводится ниже штампа «ОПИСЬ ТЕЛА» (~+28 от низа инвентаря).
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenNoteMixin extends AbstractContainerScreen<InventoryMenu> {

    public InventoryScreenNoteMixin(InventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void infection$drawNoteCard(GuiGraphics gfx, int mx, int my, float partialTick, CallbackInfo ci) {
        int cardH = InfectionNoteCard.computeHeight();

        // Под инвентарём, центр выровнен с центром инвентаря.
        int cardX = this.leftPos + (this.imageWidth - InfectionNoteCard.CARD_W) / 2;
        int cardY = this.topPos + this.imageHeight + 28;

        // Защита: если карточка не лезет вниз — клампим к низу экрана.
        if (cardY + cardH > this.height - 4) {
            cardY = Math.max(4, this.height - cardH - 4);
        }
        // Защита: если карточка ушла за левый край (узкий экран при широкой карточке) — клампим.
        if (cardX < 4) cardX = 4;

        InfectionNoteCard.draw(gfx, cardX, cardY);
    }
}
