package com.labyrinthmod.client.mixin;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import com.labyrinthmod.common.data.FractionRecipeManager;
import com.labyrinthmod.common.capability.FractionProvider;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Shadow @Final private Player player;
    @Shadow private ResultContainer resultSlots;

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void labyrinthmod$hideRestrictedResult(Container container, CallbackInfo ci) {
        if (this.player.level().isClientSide) return;

        ItemStack result = this.resultSlots.getItem(0);
        if (!result.isEmpty()) {
            String fraction = this.player.getCapability(FractionProvider.FRACTION)
                    .map(data -> data.hasFraction() ? data.getFraction().name() : "NONE")
                    .orElse("NONE");

            var recipe = this.resultSlots.getRecipeUsed();
            var itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(result.getItem());
            if ((itemId != null && itemId.toString().equals("labyrinthmod:personal_map")
                    && !fraction.equals("SURVIVOR"))
                    || !CraftRestrictionManager.canCraft(result.getItem(), fraction)
                    || (recipe != null && !FractionRecipeManager.canUse(recipe.getId(), fraction))) {
                // Очищаем слот, чтобы игрок не видел запрещенный предмет
                this.resultSlots.setItem(0, ItemStack.EMPTY);
            }
        }
    }
}
