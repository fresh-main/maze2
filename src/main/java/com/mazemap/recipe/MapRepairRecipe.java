package com.mazemap.recipe;

import com.mazemap.registry.ModItems;
import com.mazemap.util.MapDurabilityHandler;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

public class MapRepairRecipe implements CraftingRecipe {
    private final ResourceLocation id;
    private final String group;
    private final CraftingBookCategory category;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;

    public MapRepairRecipe(ResourceLocation id, String group, CraftingBookCategory category, NonNullList<Ingredient> ingredients) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.ingredients = ingredients;
        this.result = new ItemStack(ModItems.PERSONAL_MAP.get());
    }

    @Override
    public boolean matches(CraftingContainer inv, net.minecraft.world.level.Level level) {
        boolean hasMap = false, hasInk = false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.PERSONAL_MAP.get())) hasMap = true;
            if (stack.is(ModItems.INK_BOTTLE.get())) hasInk = true;
        }
        return hasMap && hasInk;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess registryAccess) {
        ItemStack map = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.PERSONAL_MAP.get())) {
                map = stack.copy();
                break;
            }
        }
        if (map.isEmpty()) return result.copy();

        int current = MapDurabilityHandler.getDurability(map);
        int repaired = Math.min(current + 100, MapDurabilityHandler.MAX_DURABILITY);
        MapDurabilityHandler.setDurability(map, repaired);
        return map;
    }

    @Override public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }
    @Override public ItemStack getResultItem(RegistryAccess registryAccess) { return result; }
    @Override public ResourceLocation getId() { return id; }
    @Override public RecipeSerializer<?> getSerializer() { return MapRepairRecipeSerializer.INSTANCE; }
    @Override public RecipeType<?> getType() { return RecipeType.CRAFTING; }
    @Override public String getGroup() { return group; }
    @Override public CraftingBookCategory category() { return category; }
    @Override public NonNullList<Ingredient> getIngredients() { return ingredients; }
}