package com.labyrinthmod.common.config;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ModConfig {

    // TEXT конфиг для блоков (простой список)
    private static final Path BLOCK_CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve(LabyrinthMod.MOD_ID + "_restricted_blocks.txt");

    // ЗАГРУЖЕННЫЙ СПИСОК БЛОКОВ (используем CopyOnWriteArrayList для потокобезопасности)
    private static final List<String> restrictedBlocks = new CopyOnWriteArrayList<>();

    // Версия конфига для отслеживания изменений
    private static long lastModifiedTime = 0;

    // Загрузка конфига
    public static void load() {
        try {
            // Создаём папку конфига если её нет
            Files.createDirectories(BLOCK_CONFIG_PATH.getParent());

            // Если файл не существует - создаём с примерами
            if (!Files.exists(BLOCK_CONFIG_PATH)) {
                createDefaultConfig();
            }

            // Загружаем конфиг
            loadFromFile();

            LabyrinthMod.LOGGER.info("[Config] Loaded {} restricted blocks from config", restrictedBlocks.size());

        } catch (IOException e) {
            LabyrinthMod.LOGGER.error("[Config] Failed to load restricted blocks config", e);
        }
    }

    // Загрузка из файла с отслеживанием изменений
    private static void loadFromFile() throws IOException {
        List<String> lines = Files.readAllLines(BLOCK_CONFIG_PATH);
        restrictedBlocks.clear();

        for (String line : lines) {
            line = line.trim();
            // Пропускаем пустые строки и комментарии
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }
            restrictedBlocks.add(line);
        }

        // Обновляем время последней модификации
        lastModifiedTime = Files.getLastModifiedTime(BLOCK_CONFIG_PATH).toMillis();
    }

    // Проверка, изменился ли конфиг с последней загрузки
    public static boolean hasConfigChanged() {
        try {
            long currentModified = Files.getLastModifiedTime(BLOCK_CONFIG_PATH).toMillis();
            return currentModified != lastModifiedTime;
        } catch (IOException e) {
            return false;
        }
    }

    // Перезагрузка конфига (для команд)
    public static void reload() {
        try {
            if (Files.exists(BLOCK_CONFIG_PATH)) {
                loadFromFile();
                LabyrinthMod.LOGGER.info("[Config] Reloaded {} restricted blocks", restrictedBlocks.size());
            }
        } catch (IOException e) {
            LabyrinthMod.LOGGER.error("[Config] Failed to reload config", e);
        }
    }

    // Создание конфига по умолчанию
    private static void createDefaultConfig() throws IOException {
        List<String> defaultConfig = new ArrayList<>();
        defaultConfig.add("# ============================================");
        defaultConfig.add("# Labyrinth Mod - Restricted Blocks Config");
        defaultConfig.add("# ============================================");
        defaultConfig.add("# Формат: modid:block_name");
        defaultConfig.add("# Примеры:");
        defaultConfig.add("");
        defaultConfig.add("# Блоки из Create");
        defaultConfig.add("# create:mechanical_press");
        defaultConfig.add("# create:basin");
        defaultConfig.add("# create:millstone");
        defaultConfig.add("");
        defaultConfig.add("# Блоки из Mekanism");
        defaultConfig.add("# mekanism:enrichment_chamber");
        defaultConfig.add("# mekanism:osmium_ore");
        defaultConfig.add("");
        defaultConfig.add("# Блоки из Thermal");
        defaultConfig.add("# thermal:machine_furnace");
        defaultConfig.add("");
        defaultConfig.add("# ===== АКТИВНЫЙ СПИСОК ЗАПРЕЩЁННЫХ БЛОКОВ =====");
        defaultConfig.add("");

        Files.write(BLOCK_CONFIG_PATH, defaultConfig);
        lastModifiedTime = Files.getLastModifiedTime(BLOCK_CONFIG_PATH).toMillis();
        LabyrinthMod.LOGGER.info("[Config] Created default config at {}", BLOCK_CONFIG_PATH);
    }

    // Получить список запрещённых блоков (неизменяемая копия)
    public static List<String> getRestrictedBlocks() {
        return Collections.unmodifiableList(restrictedBlocks);
    }

    // Проверить, запрещён ли блок
    public static boolean isBlockRestricted(String blockId) {
        return restrictedBlocks.contains(blockId);
    }

    // Сериализовать конфиг в NBT для отправки клиенту
    public static CompoundTag serializeToNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag listTag = new ListTag();
        for (String blockId : restrictedBlocks) {
            listTag.add(StringTag.valueOf(blockId));
        }
        tag.put("restrictedBlocks", listTag);
        tag.putLong("timestamp", System.currentTimeMillis());
        return tag;
    }

    // Десериализовать конфиг из NBT (на клиенте)
    public static void deserializeFromNbt(CompoundTag tag) {
        restrictedBlocks.clear();
        ListTag listTag = tag.getList("restrictedBlocks", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < listTag.size(); i++) {
            restrictedBlocks.add(listTag.getString(i));
        }
        LabyrinthMod.LOGGER.info("[Config] Synced {} restricted blocks from server", restrictedBlocks.size());
    }

    // Путь к конфигу (для админов)
    public static Path getConfigPath() {
        return BLOCK_CONFIG_PATH;
    }
}