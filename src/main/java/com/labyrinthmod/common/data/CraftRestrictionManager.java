package com.labyrinthmod.common.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CraftRestrictionManager {
    // Карта: предмет -> Набор фракций, которым ЗАПРЕЩЕНО крафтить
    private static final Map<Item, Set<String>> forbiddenFactions = new HashMap<>();

    // Путь к файлу сохранения (создастся в корне папки мода: D:\maze\labyrinthmod\...)
    private static final Path SAVE_FILE = Paths.get("labyrinthmod/craft_restrictions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void setRestrictions(Item item, Set<String> factions) {
        if (item == null) return;
        if (factions == null || factions.isEmpty()) {
            forbiddenFactions.remove(item);
        } else {
            Set<String> upperFactions = new HashSet<>();
            for (String f : factions) upperFactions.add(f.toUpperCase());
            forbiddenFactions.put(item, upperFactions);
        }
        save(); // <-- Сохраняем в файл при любом изменении
    }

    public static boolean canCraft(Item item, String playerFraction) {
        Set<String> forbidden = forbiddenFactions.get(item);
        if (forbidden == null) return true;
        return !forbidden.contains(playerFraction.toUpperCase());
    }

    public static Set<String> getForbiddenFactions(Item item) {
        return forbiddenFactions.getOrDefault(item, Collections.emptySet());
    }

    public static Map<Item, Set<String>> getAllRestrictions() {
        return new HashMap<>(forbiddenFactions);
    }

    public static void clearAll() {
        forbiddenFactions.clear();
        save(); // <-- Сохраняем очистку
    }

    // ==============================
    // === СОХРАНЕНИЕ И ЗАГРУЗКА ====
    // ==============================

    public static void save() {
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            Map<String, Set<String>> jsonMap = new HashMap<>();

            // Конвертируем Item в строку (minecraft:diamond_sword) для JSON
            for (Map.Entry<Item, Set<String>> entry : forbiddenFactions.entrySet()) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(entry.getKey());
                if (key != null) {
                    jsonMap.put(key.toString(), entry.getValue());
                }
            }

            try (Writer writer = new FileWriter(SAVE_FILE.toFile())) {
                GSON.toJson(jsonMap, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void load() {
        forbiddenFactions.clear();
        installDefaults();
        if (!Files.exists(SAVE_FILE)) return;

        boolean loaded = false;
        try (Reader reader = new FileReader(SAVE_FILE.toFile())) {
            Type type = new TypeToken<Map<String, Set<String>>>(){}.getType();
            Map<String, Set<String>> jsonMap = GSON.fromJson(reader, type);

            if (jsonMap != null) {
                for (Map.Entry<String, Set<String>> entry : jsonMap.entrySet()) {
                    ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());
                    if (rl != null) {
                        Item item = ForgeRegistries.ITEMS.getValue(rl);
                        // Проверяем, что предмет существует и это не воздух
                        if (item != null && item != Items.AIR) {
                            forbiddenFactions.put(item, entry.getValue());
                        }
                    }
                }
            }
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (loaded && addMissingBundledRestrictions()) save();
    }

    private static boolean addMissingBundledRestrictions() {
        boolean changed = false;
        try (InputStream stream = CraftRestrictionManager.class.getResourceAsStream(
                "/defaults/labyrinthmod/craft_restrictions.json")) {
            if (stream == null) throw new IOException("Missing default craft restrictions");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, Set<String>>>(){}.getType();
                Map<String, Set<String>> defaults = GSON.fromJson(reader, type);
                if (defaults == null) return false;
                for (Map.Entry<String, Set<String>> entry : defaults.entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id == null || entry.getValue() == null) continue;
                    Item item = ForgeRegistries.ITEMS.getValue(id);
                    if (item != null && item != Items.AIR && !forbiddenFactions.containsKey(item)) {
                        forbiddenFactions.put(item, new HashSet<>(entry.getValue()));
                        changed = true;
                    }
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CraftRestrictionManager.class)
                    .error("Cannot merge default craft restrictions", e);
        }
        return changed;
    }

    private static void installDefaults() {
        if (Files.exists(SAVE_FILE)) return;
        try {
            Files.createDirectories(SAVE_FILE.getParent());
            try (InputStream defaults = CraftRestrictionManager.class.getResourceAsStream(
                    "/defaults/labyrinthmod/craft_restrictions.json")) {
                if (defaults == null) throw new IOException("Missing default craft restrictions");
                Files.copy(defaults, SAVE_FILE);
            }
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(CraftRestrictionManager.class)
                    .error("Cannot install default craft restrictions", e);
        }
    }
}
