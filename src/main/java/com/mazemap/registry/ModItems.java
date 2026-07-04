package com.mazemap.registry;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.recipe.MapRepairRecipe;
import com.mazemap.recipe.MapRepairRecipeSerializer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LabyrinthMod.MOD_ID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, LabyrinthMod.MOD_ID);

    public static final RegistryObject<RecipeSerializer<MapRepairRecipe>> MAP_REPAIR =
            RECIPES.register("map_repair", () -> MapRepairRecipeSerializer.INSTANCE);

    public static final RegistryObject<Item> PERSONAL_MAP = ITEMS.register("personal_map",
            () -> new PersonalMapItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> INK_BOTTLE = ITEMS.register("ink_bottle",
            () -> new Item(new Item.Properties().stacksTo(16)));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        RECIPES.register(bus);
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PERSONAL_MAP.get());
            event.accept(INK_BOTTLE.get());
        }
    }
}
