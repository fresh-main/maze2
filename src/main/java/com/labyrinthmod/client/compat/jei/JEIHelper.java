package com.labyrinthmod.client.compat.jei;

import com.labyrinthmod.common.data.CraftRestrictionManager;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JEIHelper {
    private static IJeiRuntime runtime;
    private static final Set<CraftingRecipe> currentlyHiddenRecipes = new HashSet<>();

    public static void setRuntime(IJeiRuntime runtime) {
        JEIHelper.runtime = runtime;
    }

    public static void updateHiddenRecipes() {
        if (runtime == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        IRecipeManager jeiManager = runtime.getRecipeManager();
        RecipeManager vanillaManager = mc.level.getRecipeManager();

        List<CraftingRecipe> toHide = new ArrayList<>();
        List<CraftingRecipe> toUnhide = new ArrayList<>();

        for (Recipe<?> recipe : vanillaManager.getRecipes()) {
            if (recipe instanceof CraftingRecipe craftingRecipe) {
                ItemStack result = craftingRecipe.getResultItem(mc.level.registryAccess());

                if (!result.isEmpty()) {
                    // Используем новый метод getForbiddenFactions
                    Set<String> forbidden = CraftRestrictionManager.getForbiddenFactions(result.getItem());

                    if (!forbidden.isEmpty()) {
                        if (!currentlyHiddenRecipes.contains(craftingRecipe)) {
                            toHide.add(craftingRecipe);
                        }
                    } else {
                        if (currentlyHiddenRecipes.contains(craftingRecipe)) {
                            toUnhide.add(craftingRecipe);
                        }
                    }
                }
            }
        }

        if (!toHide.isEmpty()) {
            jeiManager.hideRecipes(RecipeTypes.CRAFTING, toHide);
            currentlyHiddenRecipes.addAll(toHide);
        }

        if (!toUnhide.isEmpty()) {
            jeiManager.unhideRecipes(RecipeTypes.CRAFTING, toUnhide);
            currentlyHiddenRecipes.removeAll(toUnhide);
        }
    }
}