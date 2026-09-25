package com.labyrinthmod.common.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.labyrinthmod.LabyrinthMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DailyQuest {
    public String id;
    public String title;
    public String description;
    public String author;
    public List<RequiredItem> requiredItems = new ArrayList<>();

    public static class RequiredItem {
        public String itemId;
        public int count;
    }

    // ═══════════════════════════════════════════════════════════════
    //  СЕРИАЛИЗАЦИЯ В NBT — НАТИВНЫЙ ФОРМАТ MINECRAFT
    // ═══════════════════════════════════════════════════════════════
    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        CompoundTag tag = new CompoundTag();
        tag.putString("QuestId", id != null ? id : "");
        tag.putString("Title", title != null ? title : "");
        tag.putString("Description", description != null ? description : "");
        tag.putString("Author", author != null ? author : "");

        ListTag itemsList = new ListTag();
        for (RequiredItem req : requiredItems) {
            CompoundTag itemTag = new CompoundTag();

            // ── Шаг 1: пробуем создать реальный ItemStack ──
            boolean resolved = false;
            try {
                String cleanedId = cleanItemId(req.itemId);
                ResourceLocation rl = new ResourceLocation(cleanedId);
                Item item = ForgeRegistries.ITEMS.getValue(rl);

                if (item != null && item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, req.count);
                    stack.save(itemTag);
                    // stack.save() записывает: {"id":"minecraft:bread","Count":32b}
                    resolved = true;

                    LabyrinthMod.LOGGER.info("[DailyQuest] ✔ Предмет '{}' x{} успешно сериализован через ItemStack",
                            cleanedId, req.count);
                } else {
                    LabyrinthMod.LOGGER.error("[DailyQuest] ✘ Предмет '{}' НЕ НАЙДЕН в ForgeRegistries!", cleanedId);
                }
            } catch (Exception e) {
                LabyrinthMod.LOGGER.error("[DailyQuest] ✘ Ошибка создания ItemStack для '{}': {}",
                        req.itemId, e.getMessage());
            }

            // ── Шаг 2: если не удалось через реестр — пишем вручную ──
            if (!resolved) {
                String cleanedId = cleanItemId(req.itemId);
                itemTag.putString("id", cleanedId);
                itemTag.putByte("Count", (byte) req.count);
            }

            // ── Шаг 3: дублируем ВСЕ возможные ключи для совместимости с GUI ──
            String cleanedId = cleanItemId(req.itemId);
            itemTag.putString("itemId", cleanedId);
            itemTag.putString("item", cleanedId);
            itemTag.putString("item_id", cleanedId);
            itemTag.putString("name", cleanedId);
            itemTag.putInt("count", req.count);
            itemTag.putInt("amount", req.count);
            itemTag.putByte("Count", (byte) req.count);

            itemsList.add(itemTag);
        }

        tag.put("RequiredItems", itemsList);
        root.put("tag", tag);
        root.put("RequiredItems", itemsList);

        // ── Финальная отладка: выводим весь NBT в лог ──
        LabyrinthMod.LOGGER.info("[DailyQuest] === NBT квеста '{}' ===", id);
        LabyrinthMod.LOGGER.info("[DailyQuest] RequiredItems count: {}", itemsList.size());
        for (int i = 0; i < itemsList.size(); i++) {
            CompoundTag t = itemsList.getCompound(i);
            LabyrinthMod.LOGGER.info("[DailyQuest]   [{}] id='{}', Count={}, count={}, itemId='{}'",
                    i,
                    t.getString("id"),
                    t.getByte("Count"),
                    t.getInt("count"),
                    t.getString("itemId"));
        }

        return root;
    }

    // ═══════════════════════════════════════════════════════════════
    //  АГРЕССИВНАЯ ОЧИСТКА СТРОКИ ПРЕДМЕТА
    //  Удаляет ВСЁ кроме [a-z0-9:_-] чтобы убить невидимые символы
    // ═══════════════════════════════════════════════════════════════
    private static String cleanItemId(String raw) {
        if (raw == null) return "minecraft:air";
        // Убираем все символы кроме допустимых в ResourceLocation
        String cleaned = raw.replaceAll("[^a-zA-Z0-9:_\\-]", "");
        if (cleaned.isEmpty()) return "minecraft:air";
        return cleaned;
    }

    // ═══════════════════════════════════════════════════════════════
    //  ГИБКИЙ ПАРСЕР JSON — ИГНОРИРУЕТ ПРОБЕЛЫ В КЛЮЧАХ
    // ═══════════════════════════════════════════════════════════════
    private static JsonElement getFlexible(JsonObject obj, String key) {
        // Точное совпадение
        if (obj.has(key)) return obj.get(key);
        // Поиск с игнорированием пробелов по краям ключа
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().trim().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String getFlexibleString(JsonObject obj, String key) {
        JsonElement el = getFlexible(obj, key);
        if (el == null) return null;
        return el.getAsString().trim();
    }

    private static int getFlexibleInt(JsonObject obj, String key, int defaultVal) {
        JsonElement el = getFlexible(obj, key);
        if (el == null) return defaultVal;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return defaultVal;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  ПАРСИНГ ИЗ JSON
    // ═══════════════════════════════════════════════════════════════
    public static DailyQuest fromJson(JsonObject json) {
        DailyQuest quest = new DailyQuest();

        quest.id          = getFlexibleString(json, "id");
        quest.title       = getFlexibleString(json, "title");
        quest.description = getFlexibleString(json, "description");
        quest.author      = getFlexibleString(json, "author");

        JsonElement reqItemsEl = getFlexible(json, "requiredItems");
        if (reqItemsEl != null && reqItemsEl.isJsonArray()) {
            for (JsonElement elem : reqItemsEl.getAsJsonArray()) {
                if (!elem.isJsonObject()) continue;
                JsonObject itemObj = elem.getAsJsonObject();
                RequiredItem req = new RequiredItem();

                // Ищем ID предмета по всем возможным ключам
                String rawId = getFlexibleString(itemObj, "item");
                if (rawId == null) rawId = getFlexibleString(itemObj, "itemId");
                if (rawId == null) rawId = getFlexibleString(itemObj, "id");
                if (rawId == null) rawId = getFlexibleString(itemObj, "name");

                if (rawId != null) {
                    req.itemId = cleanItemId(rawId);
                } else {
                    req.itemId = "minecraft:air";
                    LabyrinthMod.LOGGER.warn("[DailyQuest] В задании '{}' не найден ключ предмета! Ключи в JSON: {}",
                            quest.id, itemObj.keySet());
                }

                req.count = getFlexibleInt(itemObj, "count", 1);
                if (req.count <= 0) req.count = 1;

                LabyrinthMod.LOGGER.info("[DailyQuest] fromJson: item='{}', count={}", req.itemId, req.count);
                quest.requiredItems.add(req);
            }
        }

        return quest;
    }
}
