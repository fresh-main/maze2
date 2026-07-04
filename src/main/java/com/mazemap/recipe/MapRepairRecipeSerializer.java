package com.mazemap.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.*;

public class MapRepairRecipeSerializer implements RecipeSerializer<MapRepairRecipe> {
    public static final MapRepairRecipeSerializer INSTANCE = new MapRepairRecipeSerializer();

    @Override
    public MapRepairRecipe fromJson(ResourceLocation id, JsonObject json) {
        String group = GsonHelper.getAsString(json, "group", "");
        CraftingBookCategory category = CraftingBookCategory.CODEC.byName(
                GsonHelper.getAsString(json, "category", null), CraftingBookCategory.MISC);
        JsonArray arr = GsonHelper.getAsJsonArray(json, "ingredients");
        NonNullList<Ingredient> ingredients = NonNullList.withSize(arr.size(), Ingredient.EMPTY);
        for (int i = 0; i < arr.size(); i++) ingredients.set(i, Ingredient.fromJson(arr.get(i)));
        return new MapRepairRecipe(id, group, category, ingredients);
    }

    @Override
    public MapRepairRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        String group = buf.readUtf();
        CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
        int size = buf.readVarInt();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(size, Ingredient.EMPTY);
        for (int i = 0; i < size; i++) ingredients.set(i, Ingredient.fromNetwork(buf));
        return new MapRepairRecipe(id, group, category, ingredients);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, MapRepairRecipe recipe) {
        buf.writeUtf(recipe.getGroup());
        buf.writeEnum(recipe.category());
        buf.writeVarInt(recipe.getIngredients().size());
        for (Ingredient ingredient : recipe.getIngredients()) ingredient.toNetwork(buf);
    }
}