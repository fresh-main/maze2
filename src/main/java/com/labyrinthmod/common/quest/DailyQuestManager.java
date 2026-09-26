package com.labyrinthmod.common.quest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.labyrinthmod.LabyrinthMod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DailyQuestManager {
    private static final Path QUEST_DIR = FMLPaths.CONFIGDIR.get().resolve("maze2/daily_quests");
    private static final List<DailyQuest> loadedQuests = new ArrayList<>();
    private static final Gson GSON = new Gson();

    public static void init() {
        try {
            Files.createDirectories(QUEST_DIR);
            createExampleQuestIfEmpty();
            loadQuests();
            LabyrinthMod.LOGGER.info("[DailyQuest] Загружено {} заданий из {}", loadedQuests.size(), QUEST_DIR.toAbsolutePath());
        } catch (IOException e) {
            LabyrinthMod.LOGGER.error("[DailyQuest] Ошибка при инициализации папки заданий", e);
        }
    }

    private static void createExampleQuestIfEmpty() throws IOException {
        File[] files = QUEST_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            JsonObject example = new JsonObject();
            example.addProperty("id", "example_quest_1");
            example.addProperty("title", "Первые шаги");
            example.addProperty("description", "Соберите 10 дубовых брёвен");
            example.addProperty("reward", "100 опыта");
            example.addProperty("author", "Система");

            JsonObject reqItem = new JsonObject();
            reqItem.addProperty("item", "minecraft:oak_log");
            reqItem.addProperty("count", 10);

            var reqArray = new com.google.gson.JsonArray();
            reqArray.add(reqItem);
            example.add("requiredItems", reqArray);

            Path examplePath = QUEST_DIR.resolve("example_quest.json");
            try (FileWriter writer = new FileWriter(examplePath.toFile())) {
                GSON.toJson(example, writer);
            }
        }
    }

    public static void loadQuests() {
        loadedQuests.clear();
        File dir = QUEST_DIR.toFile();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    DailyQuest quest = DailyQuest.fromJson(json);
                    if (quest.id != null && !quest.id.isEmpty()) {
                        loadedQuests.add(quest);
                    }
                } catch (Exception e) {
                    LabyrinthMod.LOGGER.error("[DailyQuest] Ошибка парсинга файла задания: {}", file.getName(), e);
                }
            }
        }

    }

    public static List<DailyQuest> getAllQuests() {
        return loadedQuests;
    }

    public static DailyQuest getRandomQuest() {
        if (loadedQuests.isEmpty()) return null;
        return loadedQuests.get(new Random().nextInt(loadedQuests.size()));
    }

    public static List<DailyQuest> getRandomQuests(int count) {
        List<DailyQuest> shuffled = new ArrayList<>(loadedQuests);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
    public static synchronized boolean saveQuestToJson(DailyQuest quest) {
        // Генерируем уникальный ID, если он не задан
        if (quest.id == null || quest.id.isEmpty()) {
            quest.id = "custom_quest_" + System.currentTimeMillis();
        }

        String safeId = quest.id.replaceAll("[^a-zA-Z0-9_.-]", "_");
        Path filePath = QUEST_DIR.resolve(safeId + ".json");

        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("id", quest.id);
        json.addProperty("title", quest.title);
        json.addProperty("description", quest.description);
        json.addProperty("author", quest.author);

        com.google.gson.JsonArray reqArray = new com.google.gson.JsonArray();
        for (DailyQuest.RequiredItem req : quest.requiredItems) {
            com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
            itemObj.addProperty("item", req.itemId);
            itemObj.addProperty("count", req.count);
            reqArray.add(itemObj);
        }
        json.add("requiredItems", reqArray);

        try {
            Files.createDirectories(QUEST_DIR);
        } catch (IOException e) {
            LabyrinthMod.LOGGER.error("[DailyQuest] Не удалось создать папку заданий {}", QUEST_DIR.toAbsolutePath(), e);
            return false;
        }

        try (java.io.Writer writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
            LabyrinthMod.LOGGER.info("[DailyQuest] Сохранено новое задание в {}", filePath.toAbsolutePath());

            // Перезагружаем пул, чтобы новое задание сразу стало доступно для спавна
            loadQuests();
            return true;
        } catch (IOException e) {
            LabyrinthMod.LOGGER.error("[DailyQuest] Ошибка сохранения задания в JSON", e);
            return false;
        }
    }
}
