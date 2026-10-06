package com.labyrinthmod.common.patrol;

import com.labyrinthmod.common.generation.LabyrinthConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Автоматически выставляет:
 * 1) Границы лабиринта через PatrolManager.setBoundsMin/setBoundsMax.
 *    Это та же зона, которую вручную ставит BoundsStickItem.
 *
 * 2) Зону исключения спавна точек для гриверов через PatrolManager.addExclusionZone.
 *    Эта зона нужна, чтобы в области стены глейда не спавнились точки для гриверов.
 *
 * Все размеры берутся из LabyrinthConfig, поэтому если настройки сохранены
 * перед созданием мира, зона будет подстроена под них.
 */
@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LabyrinthAutoBounds {

    /*
     * Константы совпадают с LabyrinthChunkGenerator.
     * Если поменяешь толщины стен в генераторе — поменяй и здесь.
     */
    private static final int GLADE_WALL_THICKNESS = 7;
    private static final int SEPARATOR_WALL_THICKNESS = 7;
    private static final int OUTER_WALL_THICKNESS = 7;

    /*
     * В LabyrinthChunkGenerator:
     * OUTER_WALL_HEIGHT = MAZE_HEIGHT + 50;
     */
    private static final int OUTER_WALL_EXTRA_HEIGHT = 50;

    /*
     * Зона исключения для спавна точек гриверов:
     * 1 точка: Y = -64, угол стены глейда
     * 2 точка: Y = 70, противоположный угол стены глейда
     */
    private static final int GLADE_EXCLUSION_MIN_Y = -64;
    private static final int GLADE_EXCLUSION_MAX_Y = 70;

    private LabyrinthAutoBounds() {
    }

    /**
     * Основной хук: сервер уже стартовал, мир доступен,
     * PatrolManager можно получить.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        apply(event.getServer().overworld());
    }

    /**
     * Дополнительный хук на загрузку уровня.
     * Страховка на случай, если логика патрулей инициализируется раньше ServerStartedEvent.
     */
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel
                && serverLevel.dimension().equals(Level.OVERWORLD)) {
            apply(serverLevel);
        }
    }

    /**
     * Можно вызвать вручную, например после сохранения настроек в UI,
     * если нужно немедленно пересчитать зоны.
     */
    public static void apply(ServerLevel level) {
        if (level == null) {
            return;
        }

        PatrolManager manager = PatrolManager.get(level);
        if (manager == null) {
            return;
        }

        /*
         * Читаем сохранённый конфиг.
         * Если UI перед созданием мира вызвал save(), здесь будут актуальные размеры.
         */
        LabyrinthConfig cfg = LabyrinthConfig.load();

        applyLabyrinthBounds(manager, cfg);
        applyGladeSpawnExclusionZone(manager, cfg);
    }

    /*
     * =========================================================
     * 1. Границы лабиринта — аналог BoundsStickItem
     * =========================================================
     */
    private static void applyLabyrinthBounds(PatrolManager manager, LabyrinthConfig cfg) {
        /*
         * Расчёт так же, как в LabyrinthChunkGenerator:
         *
         * MAIN_MAZE_END = GLADE_RADIUS + MAIN_MAZE_WIDTH
         * SEPARATOR_WALL_END = MAIN_MAZE_END + SEPARATOR_WALL_THICKNESS
         * SECTORS_END = SEPARATOR_WALL_END + SECTOR_WIDTH
         * OUTER_WALL_END = SECTORS_END + OUTER_WALL_THICKNESS
         *
         * Где:
         * GLADE_RADIUS = cfg.gleydRadius
         * MAIN_MAZE_WIDTH = cfg.mainMazeWidth * 10
         * SECTOR_WIDTH = cfg.sectorWidth * 12
         */
        int gladeRadius = cfg.gleydRadius;
        int mainMazeWidth = cfg.mainMazeWidth * 10;
        int sectorWidth = cfg.sectorWidth * 12;

        int mainMazeEnd = gladeRadius + mainMazeWidth;
        int separatorWallEnd = mainMazeEnd + SEPARATOR_WALL_THICKNESS;
        int sectorsEnd = separatorWallEnd + sectorWidth;
        int outerWallEnd = sectorsEnd + OUTER_WALL_THICKNESS;

        /*
         * Уровень пола.
         *
         * В текущем LabyrinthChunkGenerator FLOOR_Y захардкожен как 32,
         * но в LabyrinthConfig есть поле mazeFloorY.
         *
         * Если ты хочешь на 100% повторять текущий генератор прямо сейчас,
         * замени эту строку на:
         * int floorY = 32;
         */
        int floorY = cfg.mazeFloorY;

        /*
         * Высота самой большой кольцевой стены:
         * OUTER_WALL_HEIGHT = MAZE_HEIGHT + 50
         */
        int outerWallHeight = cfg.mainMazeHeight + OUTER_WALL_EXTRA_HEIGHT;
        int outerWallTopY = floorY + outerWallHeight;

        /*
         * Точка 1:
         * уровень пола - 2, угол самой большой кольцевой стены.
         */
        BlockPos point1 = new BlockPos(
                outerWallEnd,
                floorY - 2,
                outerWallEnd
        );

        /*
         * Точка 2:
         * высота самой большой кольцевой стены,
         * противоположный угол этой же стены относительно точки 1.
         */
        BlockPos point2 = new BlockPos(
                -outerWallEnd,
                outerWallTopY,
                -outerWallEnd
        );

        BlockPos min = min(point1, point2);
        BlockPos max = max(point1, point2);

        manager.setBoundsMin(min);
        manager.setBoundsMax(max);
    }

    /*
     * =========================================================
     * 2. Зона исключения спавна точек для гриверов
     * =========================================================
     */
    private static void applyGladeSpawnExclusionZone(PatrolManager manager, LabyrinthConfig cfg) {
        /*
         * Угол стены глейда.
         *
         * В LabyrinthChunkGenerator:
         * GLADE_WALL_END = GLADE_RADIUS + GLADE_WALL_THICKNESS
         *
         * Здесь используется внешний угол стены глейда.
         * Если тебе нужен внутренний угол стены, замени на:
         * int gladeWallCorner = cfg.gleydRadius;
         */
        int gladeWallCorner = cfg.gleydRadius + GLADE_WALL_THICKNESS;

        /*
         * Точка 1:
         * Y = -64, угол стены глейда.
         */
        BlockPos point1 = new BlockPos(
                gladeWallCorner,
                GLADE_EXCLUSION_MIN_Y,
                gladeWallCorner
        );

        /*
         * Точка 2:
         * Y = 70, противоположный угол стены глейда.
         */
        BlockPos point2 = new BlockPos(
                -gladeWallCorner,
                GLADE_EXCLUSION_MAX_Y,
                -gladeWallCorner
        );

        BlockPos min = min(point1, point2);
        BlockPos max = max(point1, point2);

        /*
         * Не добавляем дубликаты при каждой загрузке мира.
         *
         * Логика:
         * 1. Находим все старые авто-зоны глейда по характерному признаку:
         *    Y от -64 до 70 и симметричный квадрат вокруг нуля.
         * 2. Удаляем только их.
         * 3. Остальные вручную добавленные exclusion zones сохраняем.
         * 4. Добавляем актуальную авто-зону.
         */
        List<PatrolManager.ExclusionZone> current = manager.getExclusionZones();
        List<PatrolManager.ExclusionZone> keep = new ArrayList<>();
        boolean changed = false;

        for (PatrolManager.ExclusionZone zone : current) {
            if (isAutoGladeExclusionZone(zone)) {
                changed = true;
            } else {
                keep.add(zone);
            }
        }

        if (!changed && containsExactZone(current, min, max)) {
            return;
        }

        manager.clearExclusionZones();

        for (PatrolManager.ExclusionZone zone : keep) {
            manager.addExclusionZone(zone.a, zone.b);
        }

        manager.addExclusionZone(min, max);
    }

    /*
     * Признак нашей авто-зоны исключения глейда:
     * - Y всегда -64 ... 70
     * - квадрат симметричен относительно 0,0
     * - minX == -maxX
     * - minZ == -maxZ
     * - |minX| == |minZ|
     *
     * Это позволяет при смене размера глейда удалить старую авто-зону
     * и поставить новую, не трогая остальные ручные exclusion zones.
     */
    private static boolean isAutoGladeExclusionZone(PatrolManager.ExclusionZone zone) {
        return zone.minY == GLADE_EXCLUSION_MIN_Y
                && zone.maxY == GLADE_EXCLUSION_MAX_Y
                && zone.minX < 0
                && zone.maxX > 0
                && zone.minZ < 0
                && zone.maxZ > 0
                && zone.minX == -zone.maxX
                && zone.minZ == -zone.maxZ
                && Math.abs(zone.minX) == Math.abs(zone.minZ);
    }

    private static boolean containsExactZone(
            List<PatrolManager.ExclusionZone> zones,
            BlockPos min,
            BlockPos max
    ) {
        for (PatrolManager.ExclusionZone zone : zones) {
            if (zone.minX == min.getX()
                    && zone.minY == min.getY()
                    && zone.minZ == min.getZ()
                    && zone.maxX == max.getX()
                    && zone.maxY == max.getY()
                    && zone.maxZ == max.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );
    }

    private static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );
    }
}