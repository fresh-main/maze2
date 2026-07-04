package com.labyrinthmod.common.event;

import com.labyrinthmod.common.capability.FractionType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class FractionSwitcherData {
    public static final Map<FractionType, Item> FRACTION_ITEMS = new HashMap<>();

    static {
        // Предметы-ассоциации для иконок
        FRACTION_ITEMS.put(FractionType.NONE, Items.BARRIER);
        FRACTION_ITEMS.put(FractionType.RUNNER, ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("sophisticatedbackpacks", "backpack")));
        FRACTION_ITEMS.put(FractionType.BUTCHER, Items.IRON_AXE);
        FRACTION_ITEMS.put(FractionType.FARMER, Items.WHEAT);
        FRACTION_ITEMS.put(FractionType.MEDIC, ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("firstaid", "bandage")));
        FRACTION_ITEMS.put(FractionType.COOK, Items.RABBIT_STEW);
        FRACTION_ITEMS.put(FractionType.OPERATOR, Items.NETHER_STAR);
        FRACTION_ITEMS.put(FractionType.IMPOSTER, ForgeRegistries.ITEMS.getValue(ResourceLocation.fromNamespaceAndPath("labyrinthmod", "traitor_tablet")));
    }

    public static ItemStack getIcon(FractionType type) {
        Item item = FRACTION_ITEMS.getOrDefault(type, Items.AIR);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    public static FractionType getNext(FractionType current) {
        FractionType[] values = FractionType.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        return values[nextIndex];
    }
}