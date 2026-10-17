package com.mazemap.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class MapDurabilityHandler {
    private static final String TAG_DURABILITY = "MapDurability";
    private static final String TAG_INFINITE = "InfiniteMap";
    public static final int MAX_DURABILITY = 1000;

    private MapDurabilityHandler() {}

    /** Получить текущую прочность */
    public static int getDurability(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_DURABILITY) ? tag.getInt(TAG_DURABILITY) : MAX_DURABILITY;
    }

    public static boolean isInfinite(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrCreateTag().getBoolean(TAG_INFINITE);
    }

    public static void setInfinite(ItemStack stack, boolean infinite) {
        if (stack.isEmpty()) return;
        stack.getOrCreateTag().putBoolean(TAG_INFINITE, infinite);
    }

    /** Установить прочность */
    public static void setDurability(ItemStack stack, int value) {
        if (stack.isEmpty()) return;
        value = Math.max(0, Math.min(value, MAX_DURABILITY));
        stack.getOrCreateTag().putInt(TAG_DURABILITY, value);
    }

    /** Потратить 1 единицу прочности. Возвращает true, если прочность успешно списана. */
    public static boolean consumeDurability(ItemStack stack) {
        return consumeDurability(stack, 1);
    }

    /** Потратить указанное количество прочности одним атомарным списанием. */
    public static boolean consumeDurability(ItemStack stack, int amount) {
        if (stack.isEmpty()) return false;
        if (isInfinite(stack)) return true;
        amount = Math.max(1, amount);
        int current = getDurability(stack);
        if (current <= 0) return false;
        setDurability(stack, current - amount);
        return true;
    }

    /** Проверка: карта сломана? */
    public static boolean isBroken(ItemStack stack) {
        return !isInfinite(stack) && getDurability(stack) <= 0;
    }

    /** Вспомогательный метод для тултипа */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        if (isInfinite(stack)) {
            tooltip.add(Component.translatable("mazemap.tooltip.infinite"));
            tooltip.add(Component.translatable("mazemap.tooltip.no_ink_cost"));
            return;
        }
        int dur = getDurability(stack);
        if (dur < MAX_DURABILITY) {
            String color = dur <= 0 ? "§4" : (dur <= 25 ? "§c" : (dur <= 50 ? "§e" : "§a"));
            tooltip.add(Component.translatable("mazemap.tooltip.durability", color + dur, MAX_DURABILITY));
            if (dur <= 0) {
                tooltip.add(Component.translatable("mazemap.tooltip.broken"));
            }
        }
    }
}
