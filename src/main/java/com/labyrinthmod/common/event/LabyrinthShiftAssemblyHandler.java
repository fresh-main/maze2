package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.contraption.LabyrinthAssembler;
import com.labyrinthmod.common.data.LabyrinthShiftZone;
import com.labyrinthmod.common.data.LabyrinthZoneSavedData;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

/**
 * Обработчик событий для сборки контрапций стен лабиринта.
 *
 * Логика:
 * 1. ChunkEvent.Load — ТОЛЬКО создание зон (один раз, при первой загрузке).
 *    НИКАКОЙ сборки контрапций здесь быть не должно: во время первичной
 *    загрузки мира это событие летит массово, пока сама система чанков ещё
 *    не до конца готова. assembleZone() трогает блоки/сущности и, будучи
 *    вызванным изнутри ChunkEvent.Load, реентерит ещё не готовый
 *    chunk-loading pipeline — это вызывает deadlock (зависание без краша,
 *    обычно на загрузке мира). Это была именно та проблема, из-за которой
 *    ранее появился текущий design — не нарушайте его снова.
 * 2. PlayerLoggedInEvent — сборка всех несобранных зон, чьи чанки уже
 *    загружены на момент входа игрока (быстрый первый проход).
 * 3. LevelTickEvent (раз в ASSEMBLY_CHECK_INTERVAL_TICKS тиков) — догоняющая
 *    прогрессивная сборка: ловит зоны, чьи чанки подгрузились ПОЗЖЕ входа
 *    игрока (например, игрок дошёл пешком до новой части лабиринта).
 *    Тик — безопасное место для этого, потому что к этому моменту мир уже
 *    полностью загружен и тикает, а не находится в процессе загрузки.
 */
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LabyrinthShiftAssemblyHandler {

    private LabyrinthShiftAssemblyHandler() {}

    // Проверяем несобранные зоны не каждый тик (это было бы дорого —
    // сканирование блоков по площади каждой зоны), а раз в N тиков.
    private static final int ASSEMBLY_CHECK_INTERVAL_TICKS = 100; // ~5 секунд

    // ============================================================
    //  1. ChunkEvent.Load — только создание зон
    // ============================================================
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator)) return;

        LabyrinthZoneSavedData data = LabyrinthZoneSavedData.get(level);

        // Создаём зоны только один раз
        if (data.hasGeneratedFromMaze()) return;
        if (!data.getAllZones().isEmpty()) {
            data.markGeneratedFromMaze();
            return;
        }

        // Пытаемся создать зоны
        try {
            LabyrinthChunkGenerator mazeGen = (LabyrinthChunkGenerator) generator;
            List<LabyrinthShiftZone> zones = mazeGen.createShiftZones();
            if (zones != null && !zones.isEmpty()) {
                for (LabyrinthShiftZone zone : zones) {
                    data.addZone(zone);
                }
                data.markGeneratedFromMaze();
                LabyrinthMod.LOGGER.info(
                        "[LabyrinthShift] Created {} shift zones (waiting for player to assemble)",
                        zones.size());
            }
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("[LabyrinthShift] Failed to create shift zones", e);
        }
    }

    // ============================================================
    //  2. PlayerLoggedInEvent — сборка при входе игрока
    // ============================================================
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.serverLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator)) return;

        LabyrinthZoneSavedData data = LabyrinthZoneSavedData.get(level);
        if (data.getAllZones().isEmpty()) {
            LabyrinthMod.LOGGER.debug(
                    "[LabyrinthShift] Player joined but no zones exist yet");
            return;
        }

        LabyrinthMod.LOGGER.info(
                "[LabyrinthShift] Player {} joined, assembling {} zones...",
                player.getName().getString(), data.getAllZones().size());

        assembleUnassembledZones(level, data);
    }

    // ============================================================
    //  3. LevelTickEvent — догоняющая прогрессивная сборка
    // ============================================================
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator)) return;

        LabyrinthAssembler.tick(level);

        // Ограничиваем частоту проверки — сканировать площадь каждой зоны
        // на каждый тик было бы неоправданно дорого.
        if (level.getGameTime() % ASSEMBLY_CHECK_INTERVAL_TICKS != 0) return;

        LabyrinthZoneSavedData data = LabyrinthZoneSavedData.get(level);
        if (data.getAllZones().isEmpty()) return;

        boolean anyUnassembled = false;
        for (LabyrinthShiftZone zone : data.getAllZones().values()) {
            if (zone.contraptionEntityId == null) {
                anyUnassembled = true;
                break;
            }
        }
        if (!anyUnassembled) return;

        assembleUnassembledZones(level, data);
    }

    // ============================================================
    //  Общая логика сборки несобранных/потерянных зон
    // ============================================================
    private static void assembleUnassembledZones(ServerLevel level, LabyrinthZoneSavedData data) {
        int assembled = 0;
        int skipped = 0;
        int failed = 0;

        for (LabyrinthShiftZone zone : data.getAllZones().values()) {
            // Пропускаем уже собранные
            if (zone.contraptionEntityId != null) {
                // Проверяем что сущность ещё жива
                OrientedContraptionEntity existing =
                        LabyrinthAssembler.findContraptionEntity(level, zone);
                if (existing != null) {
                    skipped++;
                    continue;
                }
                // Сущность потеряна — нужно пересобрать
                LabyrinthMod.LOGGER.warn(
                        "[LabyrinthShift] Zone {} entity lost, re-assembling", zone.id);
            }

            // Проверяем что чанки зоны загружены
            if (!isAreaLoaded(level, zone.minPos, zone.maxPos)) {
                continue;
            }

            // Проверяем наличие блоков
            if (!hasBlocksInArea(level, zone.minPos, zone.maxPos)) {
                continue;
            }

            // Собираем контрапцию
            try {
                UUID result = LabyrinthAssembler.assembleZone(level, zone);
                if (result != null) {
                    assembled++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                LabyrinthMod.LOGGER.error(
                        "[LabyrinthShift] Error assembling zone {}", zone.id, e);
                failed++;
            }
        }

        if (assembled > 0) {
            data.setDirty();
            LabyrinthMod.LOGGER.info(
                    "[LabyrinthShift] Assembly pass complete: assembled={}, skipped={}, failed={}",
                    assembled, skipped, failed);
        }
    }

    // ============================================================
    //  Утилиты (public — переиспользуются LabyrinthCommand)
    // ============================================================

    /**
     * Проверка загрузки области через getChunk(false) — НЕ вызывает загрузку.
     */
    public static boolean isAreaLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        int minCX = min.getX() >> 4;
        int maxCX = max.getX() >> 4;
        int minCZ = min.getZ() >> 4;
        int maxCZ = max.getZ() >> 4;

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                ChunkAccess chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) return false;
            }
        }
        return true;
    }

    public static boolean hasBlocksInArea(ServerLevel level, BlockPos min, BlockPos max) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.getBlockState(pos).isAir()) return true;
        }
        return false;
    }
}