package com.labyrinthmod.common.generation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class LabyrinthConfig {
    private static final Logger LOGGER = LogManager.getLogger();

    // Путь к файлу настроек: config/labyrinthmod/labyrinth_settings.json
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get()
            .resolve("labyrinthmod/labyrinth_settings.json").toFile();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LabyrinthConfig instance;

    public static LabyrinthConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // ==========================================
    // ★ НАСТРОЙКИ РАЗМЕРОВ (СВЯЗКА UI И ГЕНЕРАТОРА) ★
    // ==========================================
    // В UI ползунки работают в "блоках".
    // Чтобы сохранить в JSON, UI делит их. Генератор читает и умножает обратно.

    public int gleydRadius = 70;       // Радиус центральной поляны (в блоках)

    // В UI ползунок от 60 до 150. Делим на 10 -> сохраняем от 6 до 15.
    // Генератор читает и умножает на 10 -> получает 60-150 блоков.
    public int mainMazeWidth = 10;     // По умолчанию 10 (10 * 10 = 100 блоков)

    // В UI ползунок от 48 до 120. Делим на 12 -> сохраняем от 4 до 10.
    // Генератор читает и умножает на 12 -> получает 48-120 блоков.
    public int sectorWidth = 6;        // По умолчанию 6 (6 * 12 = 72 блока)

    public int mainMazeHeight = 50;    // Высота стен лабиринта (в блоках)

    // ==========================================
    // ★ ДОПОЛНИТЕЛЬНЫЕ ПАРАМЕТРЫ ★
    // ==========================================
    public int wallThickness = 2;
    public int mazeFloorY = 32;
    public int sectorHeight = 70;
    public int separatorWallHeight = 80;
    public int undergroundDepth = 130;
    public int mainMazeCellSize = 6;
    public int sectorCellSize = 8;

    public LabyrinthConfig() {
    }

    // ★ СОХРАНЕНИЕ НАСТРОЕК В ФАЙЛ ★
    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
            LOGGER.info("Saved Labyrinth config to {}", CONFIG_FILE.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Failed to save Labyrinth config", e);
        }
    }

    // ★ ЗАГРУЗКА НАСТРОЕК ИЗ ФАЙЛА ★
    public static LabyrinthConfig load() {
        LabyrinthConfig config = null;
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, LabyrinthConfig.class);
                if (config != null) {
                    LOGGER.info("Loaded Labyrinth config from {}", CONFIG_FILE.getAbsolutePath());
                    instance = config;
                    return config;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load Labyrinth config", e);
            }
        }

        // Если файла нет или загрузка не удалась - создаём новый с дефолтными значениями
        if (config == null) {
            config = new LabyrinthConfig();
        }
        config.save();
        instance = config;
        return config;
    }
}