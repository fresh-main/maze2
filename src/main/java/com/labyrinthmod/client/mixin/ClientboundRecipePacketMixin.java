package com.labyrinthmod.client.mixin;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.ArrayList;

@Mixin(ClientboundRecipePacket.class)
public class ClientboundRecipePacketMixin {

    // Перехватываем конструктор пакета, чтобы отфильтровать рецепты ДО отправки
    @Inject(method = "<init>", at = @At("TAIL"))
    private void labyrinthmod$filterRecipes(RecipeManager recipeManager, CallbackInfo ci) {
        // В 1.20.1 ClientboundRecipePacket принимает список рецептов.
        // К сожалению, напрямую модифицировать финальные поля в конструкторе через Mixin сложно.
        // Более надежный способ в Forge - использовать событие RecipesUpdatedEvent на клиенте,
        // но так как нам нужно фильтровать на сервере, мы можем использовать Accessor или
        // просто положиться на то, что CraftingMenuMixin уже не даст их скрафтить.

        // Примечание: Если вы хотите жестко убрать их из книги, лучше использовать
        // ServerGamePacketListenerImplMixin, но для простоты и стабильности,
        // Mixin слотов (Шаг 2) уже делает их бесполезными в книге.
    }
}