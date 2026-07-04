package com.mazemap.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class MapDurabilityHandler {
    private static final String TAG_DURABILITY = "MapDurability";
    public static final int MAX_DURABILITY = 1000;

    private MapDurabilityHandler() {}

    /** Получить текущую прочность */
    public static int getDurability(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TAG_DURABILITY) ? tag.getInt(TAG_DURABILITY) : MAX_DURABILITY;
    }

    /** Установить прочность */
    public static void setDurability(ItemStack stack, int value) {
        if (stack.isEmpty()) return;
        value = Math.max(0, Math.min(value, MAX_DURABILITY));
        stack.getOrCreateTag().putInt(TAG_DURABILITY, value);
    }

    /** Потратить 1 единицу прочности. Возвращает true, если прочность успешно списана. */
    public static boolean consumeDurability(ItemStack stack) {
        if (stack.isEmpty()) return false;
        int current = getDurability(stack);
        if (current <= 0) return false;
        setDurability(stack, current - 1);
        return true;
    }

    /** Проверка: карта сломана? */
    public static boolean isBroken(ItemStack stack) {
        return getDurability(stack) <= 0;
    }

    /** Вспомогательный метод для тултипа */
    public static void appendTooltip(ItemStack stack, List<Component> tooltip, TooltipFlag flag) {
        int dur = getDurability(stack);
        if (dur < MAX_DURABILITY) {
            String color = dur <= 0 ? "§4" : (dur <= 25 ? "§c" : (dur <= 50 ? "§e" : "§a"));
            tooltip.add(Component.literal("§7Прочность: " + color + dur + "§7/" + MAX_DURABILITY));
            if (dur <= 0) {
                tooltip.add(Component.literal("§4Карта сломана. Новые чанки не отрисовываются."));
            }
        }
    }
}