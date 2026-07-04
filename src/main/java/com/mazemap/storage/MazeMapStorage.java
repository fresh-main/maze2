package com.mazemap.storage;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранение нарисованных кусков карты у каждого игрока.
 *
 * Данные кладутся в ОТДЕЛЬНУЮ папку <run-dir>/mazemap-data/<player_uuid>.nbt,
 * НЕ внутри world/. Так апгрейд версии мода или пересоздание мира не сносит
 * прогресс рисования карты — игрок продолжает с тем что нарисовал.
 *
 * Формат файла — CompoundTag с полем "fragments": ListTag of CompoundTag,
 * где каждый фрагмент = (cellX, cellZ, byte[] pixels). Подробности
 * сериализации — в {@link PlayerMapData}.
 */
public final class MazeMapStorage {
    private static Path root;
    private static final Map<UUID, PlayerMapData> CACHE = new ConcurrentHashMap<>();

    private MazeMapStorage() {}

    public static void init(MinecraftServer server) {
        // server.getServerDirectory() — это рут серверного процесса (рядом с world/),
        // значит данные не попадают внутрь world и переживают апгрейд world-format'а
        // и копирование/пересоздание уровня.
        File serverDir = server.getServerDirectory();
        root = serverDir.toPath().resolve("mazemap-data");
        try {
            Files.createDirectories(root);
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("[MazeMap] failed to create storage dir {}", root, e);
        }
    }

    public static Path getRoot() {
        return root;
    }

    public static PlayerMapData get(UUID playerId) {
        return CACHE.computeIfAbsent(playerId, MazeMapStorage::load);
    }

    private static PlayerMapData load(UUID playerId) {
        Path file = root.resolve(playerId + ".nbt");
        if (!Files.exists(file)) return new PlayerMapData();
        try {
            CompoundTag tag = NbtIo.readCompressed(file.toFile());
            return PlayerMapData.fromNbt(tag);
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("[MazeMap] failed to read {}", file, e);
            return new PlayerMapData();
        }
    }

    public static void save(UUID playerId) {
        PlayerMapData data = CACHE.get(playerId);
        if (data == null || !data.isDirty()) return;
        Path file = root.resolve(playerId + ".nbt");
        try {
            NbtIo.writeCompressed(data.toNbt(), file.toFile());
            data.clearDirty();
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("[MazeMap] failed to write {}", file, e);
        }
    }

    public static void flush() {
        for (Map.Entry<UUID, PlayerMapData> e : new HashMap<>(CACHE).entrySet()) {
            if (e.getValue().isDirty()) save(e.getKey());
        }
    }

    /**
     * Сброс карты игрока: удаляет nbt-файл и инвалидирует in-memory кэш.
     * Следующий вызов get() вернёт пустой PlayerMapData.
     */
    public static void clear(UUID playerId) {
        CACHE.remove(playerId);
        if (root == null) return;
        Path file = root.resolve(playerId + ".nbt");
        try {
            Files.deleteIfExists(file);
            LabyrinthMod.LOGGER.info("[MazeMap] cleared map data for {}", playerId);
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("[MazeMap] failed to delete {}", file, e);
        }
    }
}
