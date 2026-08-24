package com.labyrinthmod.common.event;

import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CreateConfigModifier {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final int NEW_MAX_BLOCKS_MOVED = 10000;
    private static final Pattern MAX_BLOCKS_PATTERN = Pattern.compile("^(\\s*maxBlocksMoved\\s*=\\s*)\\d+(.*)$");

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // 1. Патчим файл на диске (чтобы при следующем заходе всё было ок)
        patchConfigFile(event.getServer());

        // 2. ★ МАГИЯ: Мгновенно применяем значение в оперативной памяти ★
        patchConfigInMemory();
    }

    // ==========================================
    // ★ ЧАСТЬ 1: ПАТЧ ФАЙЛА НА ДИСКЕ ★
    // ==========================================
    private static void patchConfigFile(net.minecraft.server.MinecraftServer server) {
        try {
            Path saveDir = server.getWorldPath(LevelResource.ROOT);
            Path configFile = saveDir.resolve("serverconfig").resolve("create-server.toml");
            if (!Files.exists(configFile)) return;

            List<String> lines = Files.readAllLines(configFile);
            boolean modified = false;
            List<String> newLines = new ArrayList<>();

            for (String line : lines) {
                Matcher matcher = MAX_BLOCKS_PATTERN.matcher(line);
                if (matcher.matches()) {
                    newLines.add(matcher.group(1) + NEW_MAX_BLOCKS_MOVED + matcher.group(2));
                    modified = true;
                } else {
                    newLines.add(line);
                }
            }

            if (modified) {
                Files.write(configFile, newLines);
                LOGGER.info("[CreateConfigModifier] ✓ File patched on disk!");
            }
        } catch (Exception e) {
            LOGGER.error("[CreateConfigModifier] Failed to patch file!", e);
        }
    }

    // ==========================================
    // ★ ЧАСТЬ 2: МГНОВЕННОЕ ПРИМЕНЕНИЕ В ПАМЯТИ ★
    // ==========================================
    private static void patchConfigInMemory() {
        try {
            LOGGER.info("[CreateConfigModifier] Injecting maxBlocksMoved into memory...");

            // 1. Находим контейнер мода Create
            ModContainer createContainer = ModList.get().getModContainerById("create").orElse(null);
            if (createContainer == null) {
                LOGGER.warn("[CreateConfigModifier] Create mod not found.");
                return;
            }

            // 2. Получаем доступ к внутреннему Map конфигов
            Field configsField = findField(createContainer.getClass(), "configs");
            if (configsField == null) return;
            configsField.setAccessible(true);
            Map<?, ?> configs = (Map<?, ?>) configsField.get(createContainer);

            // 3. Ищем именно серверный конфиг
            for (Object val : configs.values()) {
                if (!(val instanceof ModConfig)) continue;
                ModConfig modConfig = (ModConfig) val;
                if (modConfig.getType() != ModConfig.Type.SERVER) continue;

                // ★ ИСПРАВЛЕНИЕ ОШИБКИ 1 ★
                // getSpec() возвращает IConfigSpec, приводим его к ForgeConfigSpec
                Object specObj = modConfig.getSpec();
                if (!(specObj instanceof ForgeConfigSpec)) continue;
                ForgeConfigSpec spec = (ForgeConfigSpec) specObj;

                // 4. Ищем внутреннее хранилище значений Forge
                Field valuesField = findField(ForgeConfigSpec.class, "childConfig");
                if (valuesField == null) valuesField = findField(ForgeConfigSpec.class, "values");
                if (valuesField == null) continue;

                valuesField.setAccessible(true);
                Map<?, ?> valuesMap = (Map<?, ?>) valuesField.get(spec);

                // 5. Ищем путь, содержащий "maxBlocksMoved"
                for (Map.Entry<?, ?> entry : valuesMap.entrySet()) {
                    Object key = entry.getKey();
                    if (key instanceof List) {
                        List<?> path = (List<?>) key;
                        if (path.contains("maxBlocksMoved")) {
                            Object configValue = entry.getValue();
                            if (configValue instanceof ForgeConfigSpec.ConfigValue) {

                                // ★ ИСПРАВЛЕНИЕ ОШИБКИ 2 ★
                                // Приводим к ConfigValue<Integer>, чтобы обойти строгую проверку дженериков Java
                                @SuppressWarnings("unchecked")
                                ForgeConfigSpec.ConfigValue<Integer> cv =
                                        (ForgeConfigSpec.ConfigValue<Integer>) configValue;

                                // ★ ВЫЗЫВАЕМ СЕТТЕР ★
                                cv.set(NEW_MAX_BLOCKS_MOVED);

                                LOGGER.info("[CreateConfigModifier] ✓✓✓ SUCCESS! maxBlocksMoved is now {} in memory!", NEW_MAX_BLOCKS_MOVED);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[CreateConfigModifier] Failed to inject config in memory!", e);
        }
    }

    // Вспомогательный метод для поиска поля в классе и его родителях
    private static Field findField(Class<?> clazz, String fieldName) {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            return null;
        }
    }
}