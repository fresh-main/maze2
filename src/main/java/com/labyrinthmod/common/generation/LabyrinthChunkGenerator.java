package com.labyrinthmod.common.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.ChunkPos;

public class LabyrinthChunkGenerator extends ChunkGenerator {
    public static final Codec<LabyrinthChunkGenerator> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(LabyrinthChunkGenerator::getBiomeSource),
                    Codec.LONG.fieldOf("seed").forGetter(g -> g.seed)
            ).apply(inst, inst.stable((biomeSource, seed) -> new LabyrinthChunkGenerator(biomeSource, seed)))
    );

    // ===== ПАРАМЕТРЫ ЗОН =====
    public int GLADE_RADIUS = 70;
    private int GLADE_WALL_THICKNESS = 7;
    private int MAIN_MAZE_WIDTH = 100;
    private final int SEPARATOR_WALL_THICKNESS = 7;
    private int SECTOR_WIDTH = 72; // Было 60. +12 блоков (7 коридор + 5 стена)
    private final int OUTER_WALL_THICKNESS = 7;

    // ===== ПАРАМЕТРЫ ЛАБИРИНТА =====
    private  final int CORRIDOR_WIDTH = 5;
    private final int WALL_THICKNESS = 5;
    private int CELL_SIZE = CORRIDOR_WIDTH + WALL_THICKNESS;

    private final int FLOOR_Y = 32;
    private int MAZE_HEIGHT = 50;
    private int GLADE_WALL_HEIGHT = 60;
    private int SEPARATOR_WALL_HEIGHT = 70;
    private int OUTER_WALL_HEIGHT = 100;

    // ===== ГРАНИЦЫ ЗОН =====
    public int GLADE_WALL_END = GLADE_RADIUS + GLADE_WALL_THICKNESS;
    public int MAIN_MAZE_END = GLADE_RADIUS + MAIN_MAZE_WIDTH;
    public int SEPARATOR_WALL_END = MAIN_MAZE_END + SEPARATOR_WALL_THICKNESS;
    public int SECTORS_END = SEPARATOR_WALL_END + SECTOR_WIDTH;
    public int OUTER_WALL_END = SECTORS_END + OUTER_WALL_THICKNESS;

    private final int EXIT_WIDTH = 5;
    private int SECTOR_ENTRANCE_DISTANCE = GLADE_WALL_END + 50;

    // ★ ПАРАМЕТРЫ ПРОХОДОВ ★
    public int PASSAGE_DISTANCE = 175;
    private final int PASSAGE_WIDTH = 7;
    private final int PASSAGE_ZONE_RADIUS = 10;
    private final int PASSAGE_LENGTH = 20;
    public int PASSAGE_OFFSET = 100;
    private final int ENTRANCE_WIDTH = 15;
    private final int ENTRANCE_DEPTH = 25;



    // ===== ТОЛЬКО 3 БЛОКА ДЛЯ СТЕН =====
    private static final BlockState[] WALL_BLOCKS = {
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
    };

    private static final BlockState FLOOR_ANDESITE = Blocks.ANDESITE.defaultBlockState();
    private static final BlockState FLOOR_POLISHED_ANDESITE = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState GLADE_TOP = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState GLADE_UNDER = Blocks.DIRT.defaultBlockState();

    private final long seed;

    // ★ КЭШ ЛАБИРИНТА ★
    private final Set<Long> mazeCorridors = new HashSet<>();
    private final Set<Long> mazeWalls = new HashSet<>();
    private final Set<Long> sectorCorridors = new HashSet<>();
    private final Set<Long> sectorWalls = new HashSet<>();
    private final Set<Long> gladeExits = new HashSet<>();
    private final Set<Long> passages = new HashSet<>(); // ★ ВСЕ ПРОХОДЫ ★
    private final Set<Long> passageZones = new HashSet<>();// ★ РАСШИРЕННЫЕ ЗОНЫ ★
    private final ImprovedNoise terrainNoise;
    private final ImprovedNoise featureNoise;
    private final LabyrinthConfig config;

    public LabyrinthChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;

        // ★ ЧИТАЕМ ГЛОБАЛЬНЫЙ КОНФИГ ★
        LabyrinthConfig cfg = LabyrinthConfig.getInstance();
        this.config = cfg;

        // Инициализация размеров из конфига.
        // (Если в UI ты меняешь ползунки, они сохраняются в конфиг, и здесь подхватываются)
        this.GLADE_RADIUS = cfg.gleydRadius;
        this.MAIN_MAZE_WIDTH = cfg.mainMazeWidth * 10; // Множитель для ширины лабиринта
        this.SECTOR_WIDTH = cfg.sectorWidth * 12;      // Кратно 12 для сетки секторов
        this.MAZE_HEIGHT = cfg.mainMazeHeight;

        this.CELL_SIZE = CORRIDOR_WIDTH + WALL_THICKNESS;

        this.GLADE_WALL_HEIGHT = MAZE_HEIGHT + 10;
        this.SEPARATOR_WALL_HEIGHT = MAZE_HEIGHT + 20;
        this.OUTER_WALL_HEIGHT = MAZE_HEIGHT + 50;

        // Динамический расчет границ
        this.GLADE_WALL_END = GLADE_RADIUS + GLADE_WALL_THICKNESS;
        this.MAIN_MAZE_END = GLADE_RADIUS + MAIN_MAZE_WIDTH;
        this.SEPARATOR_WALL_END = MAIN_MAZE_END + SEPARATOR_WALL_THICKNESS;
        this.SECTORS_END = SEPARATOR_WALL_END + SECTOR_WIDTH;
        this.OUTER_WALL_END = SECTORS_END + OUTER_WALL_THICKNESS;

        this.SECTOR_ENTRANCE_DISTANCE = GLADE_WALL_END + 50;

        // Проходы теперь всегда ровно по центру кольца секторов
        this.PASSAGE_DISTANCE = SEPARATOR_WALL_END;
        this.PASSAGE_OFFSET = MAIN_MAZE_WIDTH;

        this.terrainNoise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(seed));
        this.featureNoise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(seed ^ 0x123456789ABCDEFL));

        generateMaze();
        generateSectors();
        generatePassages();
        validateAndFixRealPassages();
    }

    private void generateMaze() {
        mazeCorridors.clear();
        mazeWalls.clear();
        gladeExits.clear();

        Random rand = new Random(seed);

        // ★ ДИНАМИЧЕСКИЙ РАСЧЕТ РАЗМЕРА СЕТКИ ★
        int maxRadius = MAIN_MAZE_END;
        int CENTER = (maxRadius / 5) + 2;
        if (CENTER % 2 == 0) CENTER++;
        int GRID_SIZE = CENTER * 2 + 1;

        boolean[][] isValid = new boolean[GRID_SIZE][GRID_SIZE];
        boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                int wx = (i - CENTER) * 5;
                int wz = (j - CENTER) * 5;
                int dist = Math.max(Math.abs(wx), Math.abs(wz));

                if (dist >= GLADE_WALL_END + 1 && dist <= SEPARATOR_WALL_END - 5) {
                    isValid[i][j] = true;
                    if (i % 2 == 0 && j % 2 == 0) {
                        grid[i][j] = false;
                    } else {
                        grid[i][j] = true;
                    }
                }
            }
        }

        // ★ ДИНАМИЧЕСКИЕ ВХОДЫ (4 штуки) ★
        int eDist = (GLADE_WALL_END / 5) + 2;
        int eWidth = 1;

        int[][] entrances = {
                {CENTER - eWidth, CENTER - eDist - 3, CENTER + eWidth, CENTER - eDist},
                {CENTER - eWidth, CENTER + eDist, CENTER + eWidth, CENTER + eDist + 3},
                {CENTER - eDist - 3, CENTER - eWidth, CENTER - eDist, CENTER + eWidth},
                {CENTER + eDist, CENTER - eWidth, CENTER + eDist + 3, CENTER + eWidth}
        };

        for (int[] e : entrances) {
            for (int i = e[0]; i <= e[2]; i++) {
                for (int j = e[1]; j <= e[3]; j++) {
                    if (i >= 0 && i < GRID_SIZE && j >= 0 && j < GRID_SIZE) {
                        isValid[i][j] = true;
                        grid[i][j] = false;
                        int wx = (i - CENTER) * 5;
                        int wz = (j - CENTER) * 5;
                        for (int dx = 0; dx < 5; dx++) {
                            for (int dz = 0; dz < 5; dz++) {
                                long h = hash(wx + dx, wz + dz);
                                mazeCorridors.add(h);
                                gladeExits.add(h);
                                mazeWalls.remove(h);
                            }
                        }
                    }
                }
            }
        }

        // ★ ДИНАМИЧЕСКИЕ ВЫХОДЫ В СЕКТОРА (8 штук) ★
        int exitDist = (MAIN_MAZE_END / 5) - 2;

        int[][] exits = {
                {CENTER - eWidth, CENTER - exitDist - 2, CENTER + eWidth, CENTER - exitDist},
                {CENTER - eWidth, CENTER + exitDist, CENTER + eWidth, CENTER + exitDist + 2},
                {CENTER - exitDist - 2, CENTER - eWidth, CENTER - exitDist, CENTER + eWidth},
                {CENTER + exitDist, CENTER - eWidth, CENTER + exitDist + 2, CENTER + eWidth}
        };

        int diagDist = (int)(exitDist * 0.707);
        int[][] diagExits = {
                {CENTER + diagDist, CENTER - diagDist - 2, CENTER + diagDist + 2, CENTER - diagDist},
                {CENTER + diagDist, CENTER + diagDist, CENTER + diagDist + 2, CENTER + diagDist + 2},
                {CENTER - diagDist - 2, CENTER + diagDist, CENTER - diagDist, CENTER + diagDist + 2},
                {CENTER - diagDist - 2, CENTER - diagDist - 2, CENTER - diagDist, CENTER - diagDist}
        };

        int[][] allExits = new int[8][4];
        System.arraycopy(exits, 0, allExits, 0, 4);
        System.arraycopy(diagExits, 0, allExits, 4, 4);

        for (int[] ex : allExits) {
            for (int i = ex[0]; i <= ex[2]; i++) {
                for (int j = ex[1]; j <= ex[3]; j++) {
                    if (i >= 0 && i < GRID_SIZE && j >= 0 && j < GRID_SIZE) {
                        isValid[i][j] = true;
                        grid[i][j] = false;
                    }
                }
            }
        }

        // ===== АЛГОРИТМ КРУСКАЛА (ОСТАВЛЯЕМ ТВОЙ СТАРЫЙ КОД БЕЗ ИЗМЕНЕНИЙ) =====
        Map<String, Integer> cellToId = new HashMap<>();
        List<int[]> idToCell = new ArrayList<>();
        int idCounter = 0;

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (isValid[i][j]) {
                    cellToId.put(i + ", " + j, idCounter);
                    idToCell.add(new int[]{i, j});
                    idCounter++;
                }
            }
        }

        int[] parent = new int[idCounter];
        for (int i = 0; i < idCounter; i++) parent[i] = i;

        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE; i += 2) {
            for (int j = 0; j < GRID_SIZE; j += 2) {
                if (!isValid[i][j]) continue;
                Integer u = cellToId.get(i + ", " + j);
                if (u == null) continue;

                if (i + 2 < GRID_SIZE && isValid[i + 2][j]) {
                    Integer v = cellToId.get((i + 2) + ", " + j);
                    if (v != null) edges.add(new int[]{u, v, i + 1, j});
                }
                if (j + 2 < GRID_SIZE && isValid[i][j + 2]) {
                    Integer v = cellToId.get(i + ", " + (j + 2));
                    if (v != null) edges.add(new int[]{u, v, i, j + 1});
                }
            }
        }

        Collections.shuffle(edges, rand);

        for (int[] edge : edges) {
            int u = edge[0];
            int v = edge[1];
            int wi = edge[2];
            int wj = edge[3];

            if (find(parent, u) != find(parent, v)) {
                union(parent, u, v);
                grid[wi][wj] = false;
            }
        }

        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = 0; j < GRID_SIZE; j++) {
                if (!isValid[i][j]) continue;

                int wx = (i - CENTER) * 5;
                int wz = (j - CENTER) * 5;

                if (!grid[i][j]) {
                    for (int dx = 0; dx < 5; dx++) {
                        for (int dz = 0; dz < 5; dz++) {
                            mazeCorridors.add(hash(wx + dx, wz + dz));
                        }
                    }
                } else {
                    for (int dx = 0; dx < 5; dx++) {
                        for (int dz = 0; dz < 5; dz++) {
                            mazeWalls.add(hash(wx + dx, wz + dz));
                        }
                    }
                }
            }
        }

        for (long exitHash : gladeExits) {
            mazeWalls.remove(exitHash);
        }
    }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ КРУСКАЛА =====
    private int find(int[] parent, int i) {
        if (parent[i] == i) return i;
        return parent[i] = find(parent, parent[i]); // Сжатие пути
    }

    private void union(int[] parent, int i, int j) {
        int rootI = find(parent, i);
        int rootJ = find(parent, j);
        if (rootI != rootJ) {
            parent[rootI] = rootJ;
        }
    }

    // ===== ГЕНЕРАЦИЯ СЕКТОРОВ (АЛГОРИТМ КРУСКАЛА) =====
    private void generateSectors() {
        sectorCorridors.clear();
        sectorWalls.clear();

        // 1. ГЕНЕРИРУЕМ ЛАБИРИНТ ДЛЯ ОДНОГО ОКТАНТА (Правый нижний: x > z > 0)
        // Ширина коридора 7 блоков, толщина стены 5 блоков. Шаг сетки = 12 блоков.
        int MIN_U = (SEPARATOR_WALL_END + 4) / 12;

        // ВНЕШНЯЯ ГРАНИЦА: Коридор не должен заходить на Внешнюю стену (начинается после SECTORS_END).
        // Центр коридора (cx) плюс 3 должен быть <= SECTORS_END => cx <= SECTORS_END - 3
        int MAX_U = (SECTORS_END - 3) / 12;

        List<int[]> rooms = new ArrayList<>();
        List<int[]> roomCenters = new ArrayList<>();
        Map<String, Integer> roomToId = new HashMap<>();
        int idCounter = 0;

        // Собираем все валидные "комнаты" для октанта 0
        for (int u = MIN_U; u <= MAX_U; u++) {
            // v > 0 и v < u (строгое неравенство гарантирует зазор для диагональных стен)
            for (int v = 1; v < u; v++) {
                rooms.add(new int[]{u, v});
                roomToId.put(u + ", " + v, idCounter++);
                roomCenters.add(new int[]{12 * u, 12 * v});
            }
        }

        int[] parent = new int[idCounter];
        for (int i = 0; i < idCounter; i++) parent[i] = i;

        List<int[]> edges = new ArrayList<>();
        for (int[] room : rooms) {
            int u = room[0];
            int v = room[1];
            int uId = roomToId.get(u + ", " + v);

            // Сосед справа (u + 1)
            if (u + 1 <= MAX_U && roomToId.containsKey((u + 1) + ", " + v)) {
                // Центр стены между u и u+1: X = 12 * u + 6, Z = 12 * v
                edges.add(new int[]{uId, roomToId.get((u + 1) + ", " + v), 12 * u + 6, 12 * v});
            }
            // Сосед сверху (v + 1)
            if (v + 1 < u && roomToId.containsKey(u + ", " + (v + 1))) {
                // Центр стены между v и v+1: X = 12 * u, Z = 12 * v + 6
                edges.add(new int[]{uId, roomToId.get(u + ", " + (v + 1)), 12 * u, 12 * v + 6});
            }
        }

        Random rand = new Random(seed + 999L);
        Collections.shuffle(edges, rand);

        List<int[]> brokenWalls = new ArrayList<>();

        // Выполняем алгоритм Крускала
        for (int[] edge : edges) {
            if (find(parent, edge[0]) != find(parent, edge[1])) {
                union(parent, edge[0], edge[1]);
                // Сохраняем координаты центра пробитой стены
                brokenWalls.add(new int[]{edge[2], edge[3]});
            }
        }

        // 2. ПРИМЕНЕНИЕ СИММЕТРИИ (8 октантов)
        // Применяем симметрию к комнатам (7x7)
        for (int[] center : roomCenters) {
            int cx = center[0];
            int cz = center[1];

            addSectorCorridor7x7(cx, cz);   // Октант 0
            addSectorCorridor7x7(cz, cx);   // Октант 1
            addSectorCorridor7x7(-cz, cx);  // Октант 2
            addSectorCorridor7x7(-cx, cz);  // Октант 3
            addSectorCorridor7x7(-cx, -cz); // Октант 4
            addSectorCorridor7x7(-cz, -cx); // Октант 5
            addSectorCorridor7x7(cz, -cx);  // Октант 6
            addSectorCorridor7x7(cx, -cz);  // Октант 7
        }

        // Применяем симметрию к пробитым стенам (ТОЖЕ 7x7)
        for (int[] wall : brokenWalls) {
            int wx = wall[0];
            int wz = wall[1];

            addSectorWallBreak(wx, wz);
            addSectorWallBreak(wz, wx);
            addSectorWallBreak(-wz, wx);
            addSectorWallBreak(-wx, wz);
            addSectorWallBreak(-wx, -wz);
            addSectorWallBreak(-wz, -wx);
            addSectorWallBreak(wz, -wx);
            addSectorWallBreak(wx, -wz);
        }
    }

    // ★ Вспомогательный метод для добавления комнаты 7x7 в коридоры сектора
    private void addSectorCorridor7x7(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                sectorCorridors.add(hash(cx + dx, cz + dz));
            }
        }
    }

    // ★ Вспомогательный метод для пробития стены 7x7 между комнатами
// ★ ИСПРАВЛЕНО: теперь от -3 до 3 (7 блоков), чтобы стыковалось с комнатами!
    private void addSectorWallBreak(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                sectorCorridors.add(hash(cx + dx, cz + dz));
            }
        }
    }


    // Вспомогательный метод для добавления комнаты 5x5 в коридоры сектора
    private void addSectorCorridor(int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                sectorCorridors.add(hash(cx + dx, cz + dz));
            }
        }
    }

    private BlockState generateSectorBlock(int x, int y, int z, long hash) {
        if (y == FLOOR_Y) return randomFloorBlock();
        int dist = Math.max(Math.abs(x), Math.abs(z));
        boolean isInternalWall = (Math.abs(x) <= 2 || Math.abs(z) <= 2 || Math.abs(x - z) <= 2 || Math.abs(x + z) <= 2);
        int wallHeight = (dist >= SECTORS_END || isInternalWall) ? SEPARATOR_WALL_HEIGHT : MAZE_HEIGHT;
        if (y > FLOOR_Y && y <= FLOOR_Y + wallHeight) {
            if (passages.contains(hash) || passageZones.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                return Blocks.AIR.defaultBlockState();
            }
            if (sectorCorridors.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                return Blocks.AIR.defaultBlockState();
            }
            int depthFromSurface;
            if (isInternalWall) {
                depthFromSurface = (Math.abs(x) == 3 || Math.abs(z) == 3 || Math.abs(x - z) == 3 || Math.abs(x + z) == 3) ? 0 : 1;
            } else {
                depthFromSurface = SECTORS_END - dist;
            }
            int yDepth = (FLOOR_Y + wallHeight) - y;
            depthFromSurface = Math.min(depthFromSurface, yDepth);
            return getDecayedWallBlock(x, y, z, depthFromSurface);
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * ★ ВСПОМОГАТЕЛЬНЫЙ МЕТОД ★
     * Вычисляет физическую стартовую координату для логического индекса k,
     * учитывая разную ширину комнат (7) и стен (5).
     */
    private int getPhysicalStart(int k, int corridorWidth, int wallThickness) {
        if (k % 2 == 0) {
            // Комната: каждая пара (комната+стена) занимает 12 блоков
            return (k / 2) * (corridorWidth + wallThickness);
        } else {
            // Стена: сдвиг на ширину комнаты
            return ((k - 1) / 2) * (corridorWidth + wallThickness) + corridorWidth;
        }
    }

    /**
     * ★ ГЕНЕРАЦИЯ ПРОХОДОВ ★
     * По 2 прохода с каждой стороны на расстоянии 175 от центра
     */
    /**
     * ★ ГЕНЕРАЦИЯ ВСЕХ ПРОХОДОВ ★
     */
    /**
     * ★ ГЕНЕРАЦИЯ ВСЕХ ПРОХОДОВ ★
     */
    private void generatePassages() {
        passages.clear();
        passageZones.clear();

        // ★ 1. ПРОХОДЫ В СЕКТОРА (8 штук) ★
        // Смещение от осей X и Z (середина ширины сектора)
        int pOff = SECTOR_WIDTH / 2;
        // Начинаем проход чуть внутри основного лабиринта, чтобы соединиться с его сеткой
        int startDist = MAIN_MAZE_END - 15;

        int[][] passageConfigs = {
                // Северная сторона
                {-pOff, -startDist, 0, -1}, {pOff, -startDist, 0, -1},
                // Южная сторона
                {-pOff, startDist, 0, 1}, {pOff, startDist, 0, 1},
                // Западная сторона
                {-startDist, -pOff, -1, 0}, {-startDist, pOff, -1, 0},
                // Восточная сторона
                {startDist, -pOff, 1, 0}, {startDist, pOff, 1, 0}
        };

        for (int[] config : passageConfigs) {
            createPassage(config[0], config[1], config[2], config[3]);
        }

        // ★ 2. ПРОХОДЫ В ГЛЕЙД (4 штуки) ★
        createGladePassage(0, -(GLADE_RADIUS+5), 0, 1);  // Север
        createGladePassage(0, (GLADE_RADIUS+5), 0, -1);  // Юг
        createGladePassage(-(GLADE_RADIUS+5), 0, 1, 0);  // Запад
        createGladePassage((GLADE_RADIUS+5), 0, -1, 0);  // Восток
    }

    /**
     * ★ СОЗДАНИЕ ОДНОГО ПРОХОДА В СЕКТОРА ★
     * Динамически пробивает туннель от основного лабиринта до внешней границы секторов.
     */
    private void createPassage(int offsetX, int offsetZ, int dirX, int dirZ) {
        int halfWidth = PASSAGE_WIDTH / 2;

        // ★ ДИНАМИЧЕСКИЙ РАСЧЕТ ГРАНИЦ ПРОХОДА ★
        // Стартуем внутри основного лабиринта
        int startDist = MAIN_MAZE_END - 15;
        // Заканчиваем за внешней границей секторов (чтобы гарантированно пробить внешнюю стену секторов)
        int endDist = SEPARATOR_WALL_END + 15;
        int totalLength = endDist - startDist;

        // Корректируем стартовую координату вдоль оси движения
        int startX = offsetX;
        int startZ = offsetZ;
        if (dirZ != 0) startZ = (dirZ > 0) ? startDist : -startDist;
        if (dirX != 0) startX = (dirX > 0) ? startDist : -startDist;

        // ==========================================
        // 1. ГЛАВНЫЙ ТОННЕЛЬ (Пробивает всё насквозь)
        // ==========================================
        // Проходит через: основной лабиринт -> разделительную стену -> сектора -> внешнюю границу
        for (int step = 0; step <= totalLength; step++) {
            int px = startX + dirX * step;
            int pz = startZ + dirZ * step;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int wx = (dirX != 0) ? px : px + w;
                int wz = (dirZ != 0) ? pz : pz + w;

                long h = hash(wx, wz);
                passages.add(h);

                // ★ ГАРАНТИРОВАННО очищаем от любых стен ★
                mazeWalls.remove(h);
                sectorWalls.remove(h);

                // ★ Добавляем в коридоры обеих зон ★
                mazeCorridors.add(h);
                sectorCorridors.add(h);
            }
        }

        // ==========================================
        // 2. ПЛОЩАДКА ПЕРЕД ВХОДОМ (со стороны лабиринта)
        // ==========================================
        int preX = startX - dirX * 8;
        int preZ = startZ - dirZ * 8;

        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int wx = preX + dx;
                int wz = preZ + dz;

                long h = hash(wx, wz);
                passages.add(h);
                mazeWalls.remove(h);
                mazeCorridors.add(h);
            }
        }
    }

    /**
     * ★ СОЗДАНИЕ ПРОХОДА ЧЕРЕЗ СТЕНУ ГЛЕЙДА ★
     * Прорубает стену глейда и соединяет её с сеткой основного лабиринта.
     */
    private void createGladePassage(int centerX, int centerZ, int dirX, int dirZ) {
        int halfWidth = 7; // Ширина 15 блоков (идеально совпадает с 3 клетками сетки лабиринта)

        // step от -10 до 25 покрывает диапазон от 65 до 90 по оси направления.
        // Это гарантированно прорывает стену глейда (70-77) и соединяется с сеткой лабиринта (начинается на 85).
        for (int step = -10; step <= 25; step++) {
            int px = centerX + dirX * step;
            int pz = centerZ + dirZ * step;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int wx, wz;
                if (dirX != 0) {
                    wx = px;
                    wz = pz + w;
                } else {
                    wx = px + w;
                    wz = pz;
                }

                long h = hash(wx, wz);
                passages.add(h);

                // ★ КЛЮЧЕВОЕ: помечаем для прорубания стены глейда в generateLabyrinth
                gladeExits.add(h);

                mazeWalls.remove(h);
                sectorWalls.remove(h);
                mazeCorridors.add(h);
            }
        }
    }



    private long hash(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    // ===== ОСНОВНЫЕ МЕТОДЫ =====
    @Override
    @NotNull
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void buildSurface(@NotNull WorldGenRegion region, @NotNull StructureManager structureManager,
                             @NotNull RandomState random, @NotNull ChunkAccess chunk) {}

    @Override
    public void applyCarvers(@NotNull WorldGenRegion region, long seed, @NotNull RandomState random,
                             @NotNull BiomeManager biomeManager, @NotNull StructureManager structureManager,
                             @NotNull ChunkAccess chunk, @NotNull GenerationStep.Carving carving) {}

    @Override
    public void spawnOriginalMobs(@NotNull WorldGenRegion region) {}



    @Override
    @NotNull
    public CompletableFuture<ChunkAccess> fillFromNoise(@NotNull Executor executor, @NotNull Blender blender,
                                                        @NotNull RandomState random, @NotNull StructureManager structureManager,
                                                        @NotNull ChunkAccess chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int secY = chunk.getMinSection(); secY < chunk.getMaxSection(); secY++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(secY));
            int baseY = secY << 4;
            for (int localX = 0; localX < 16; localX++) {
                int worldX = (chunkX << 4) + localX;
                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = (chunkZ << 4) + localZ;
                    // ★ ОПТИМИЗАЦИЯ: Вычисляем константы для колонки 1 раз ДО цикла Y
                    fillColumnFast(section, baseY, localX, localZ, worldX, worldZ);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    // ★ НОВЫЙ БЫСТРЫЙ МЕТОД ЗАПОЛНЕНИЯ КОЛОНКИ ★
    private void fillColumnFast(LevelChunkSection section, int baseY, int localX, int localZ, int worldX, int worldZ) {
        int dist = Math.max(Math.abs(worldX), Math.abs(worldZ));
        long hash = hash(worldX, worldZ);
        int topY = getTopY(worldX, worldZ); // ★ Считаем высоту крыши 1 раз на всю колонку!
        boolean isNatural = (dist <= GLADE_RADIUS || dist > OUTER_WALL_END);

        for (int localY = 0; localY < 16; localY++) {
            int worldY = baseY + localY;
            BlockState state;

            if (isNatural) {
                state = generateNaturalTerrain(worldX, worldY, worldZ, dist);
            } else {
                if (worldY < FLOOR_Y) {
                    state = getWallBlock(worldX, worldY, worldZ);
                } else {
                    // ★ Передаем готовые hash, dist, topY, чтобы не пересчитывать их 384 раза
                    state = generateLabyrinthFast(worldX, worldY, worldZ, dist, hash, topY);
                }
            }
            if (state == null) state = Blocks.AIR.defaultBlockState();
            section.setBlockState(localX, localY, localZ, state, false);
        }
    }

    // ★ ОПТИМИЗИРОВАННАЯ ГЕНЕРАЦИЯ ЛАБИРИНТА ★
    private BlockState generateLabyrinthFast(int x, int y, int z, int dist, long hash, int topY) {
        if (y > topY) return Blocks.AIR.defaultBlockState(); // ★ Досрочный выход, если мы выше крыши

        BlockState state = Blocks.AIR.defaultBlockState();

        if (dist <= GLADE_WALL_END) {
            if (gladeExits.contains(hash) && y < FLOOR_Y + GLADE_WALL_HEIGHT) {
                state = Blocks.AIR.defaultBlockState();
            } else if (y >= FLOOR_Y) {
                int depthFromSurface = calcDepth(dist, GLADE_RADIUS, GLADE_WALL_END, y, GLADE_WALL_HEIGHT);
                state = getDecayedWallBlock(x, y, z, depthFromSurface);
            }
        } else if (dist < MAIN_MAZE_END) {
            state = generateMainMazeBlock(x, y, z, hash);
        } else if (dist <= SEPARATOR_WALL_END) {
            if (passages.contains(hash) && y < FLOOR_Y + SEPARATOR_WALL_HEIGHT) {
                state = Blocks.AIR.defaultBlockState();
            } else if (y >= FLOOR_Y) {
                int depthFromSurface = calcDepth(dist, MAIN_MAZE_END, SEPARATOR_WALL_END, y, SEPARATOR_WALL_HEIGHT);
                state = getDecayedWallBlock(x, y, z, depthFromSurface);
            }
        } else if (dist <= SECTORS_END) {
            state = generateSectorBlock(x, y, z, hash);
        } else if (dist <= OUTER_WALL_END) {
            if (y >= FLOOR_Y) {
                int depthFromSurface = calcDepth(dist, SECTORS_END, OUTER_WALL_END, y, OUTER_WALL_HEIGHT);
                state = getDecayedWallBlock(x, y, z, depthFromSurface);
            }
        }
        return state;
    }

    // ★ Вспомогательный метод для расчета глубины от поверхности (убирает дублирование кода)
    private int calcDepth(int dist, int innerBound, int outerBound, int y, int wallHeight) {
        int depthXZ;
        if (dist <= innerBound + 3) depthXZ = dist - (innerBound + 1);
        else depthXZ = outerBound - dist;

        int depthY = (FLOOR_Y + wallHeight) - y;
        return Math.min(depthXZ, depthY);
    }

    private BlockState generateUnderground(int x, int y, int z) {
        if (y <= -64) return Blocks.BEDROCK.defaultBlockState();

        long seed = this.seed ^ (x * 31L + y * 17L + z * 13L);
        Random rand = new Random(seed);

        if (y < 0 && rand.nextDouble() < 0.025) return Blocks.COAL_ORE.defaultBlockState();
        if (y < -10 && rand.nextDouble() < 0.020) return Blocks.COAL_ORE.defaultBlockState();
        if (y < -10 && rand.nextDouble() < 0.018) return Blocks.IRON_ORE.defaultBlockState();
        if (y < -20 && rand.nextDouble() < 0.012) return Blocks.IRON_ORE.defaultBlockState();
        if (y < -30 && rand.nextDouble() < 0.008) return Blocks.GOLD_ORE.defaultBlockState();
        if (y < -40 && rand.nextDouble() < 0.005) return Blocks.GOLD_ORE.defaultBlockState();
        if (y < -50 && rand.nextDouble() < 0.003) return Blocks.DIAMOND_ORE.defaultBlockState();
        if (y < -55 && rand.nextDouble() < 0.002) return Blocks.DIAMOND_ORE.defaultBlockState();
        if (y < -20 && rand.nextDouble() < 0.001) return Blocks.EMERALD_ORE.defaultBlockState();
        if (y < -20 && rand.nextDouble() < 0.010) return Blocks.REDSTONE_ORE.defaultBlockState();
        if (y < -30 && rand.nextDouble() < 0.008) return Blocks.REDSTONE_ORE.defaultBlockState();
        if (y < -20 && rand.nextDouble() < 0.005) return Blocks.LAPIS_ORE.defaultBlockState();

        return Blocks.STONE.defaultBlockState();
    }
    /**
     * ★ МЕТОД: Старение и декор (С защитой фундамента) ★
     * 1. Нижние 5 блоков от пола абсолютно монолитны (никаких дыр и трещин)
     * 2. Ширина динамическая (2-5 блоков)
     * 3. Длина ограничена (обрезается маской, превращаясь в короткие сегменты)
     * 4. Глубина строго ограничена (1-3 блока, не пробивает насквозь)
     */
    // НОВЫЙ КЛАСС ДЛЯ КЭШИРОВАНИЯ NOISE
    private static class ChunkNoiseCache {
        final double[] coreNoise = new double[256];
        final double[] widthMod = new double[256];
        final double[] breakNoise = new double[256];
        final double[] depthMod = new double[256];
        final double[] rebarChance = new double[256];
        final int chunkX;
        final int chunkZ;
        long lastAccessTime;

        ChunkNoiseCache(int chunkX, int chunkZ, ImprovedNoise featureNoise, ImprovedNoise terrainNoise) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.lastAccessTime = System.currentTimeMillis();

            // ПРЕДВЫЧИСЛЯЕМ ВСЕ NOISE ЗНАЧЕНИЯ ОДИН РАЗ ДЛЯ ЧАНКА
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldX = chunkX * 16 + localX;
                    int worldZ = chunkZ * 16 + localZ;
                    int index = localX * 16 + localZ;

                    coreNoise[index] = featureNoise.noise(worldX * 0.04, 0, worldZ * 0.04);
                    widthMod[index] = terrainNoise.noise(worldX * 0.015, 0, worldZ * 0.015);
                    breakNoise[index] = featureNoise.noise(worldX * 0.08 + 500.0, 0, worldZ * 0.08);
                    depthMod[index] = terrainNoise.noise(worldX * 0.1, 0, worldZ * 0.1);
                    rebarChance[index] = featureNoise.noise(worldX * 0.25, 0, worldZ * 0.25);
                }
            }
        }

        double getCoreNoise(int localX, int localZ) {
            return coreNoise[localX * 16 + localZ];
        }

        double getWidthMod(int localX, int localZ) {
            return widthMod[localX * 16 + localZ];
        }

        double getBreakNoise(int localX, int localZ) {
            return breakNoise[localX * 16 + localZ];
        }

        double getDepthMod(int localX, int localZ) {
            return depthMod[localX * 16 + localZ];
        }

        double getRebarChance(int localX, int localZ) {
            return rebarChance[localX * 16 + localZ];
        }
    }

    // НОВЫЙ КЭШ С LRU-СТРАТЕГИЕЙ
    private static final int MAX_CACHE_SIZE = 500;
    private static final Map<Long, ChunkNoiseCache> noiseCache = new ConcurrentHashMap<>();

    private ChunkNoiseCache getOrCreateNoiseCache(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);

        ChunkNoiseCache cache = noiseCache.get(key);
        if (cache != null) {
            cache.lastAccessTime = System.currentTimeMillis();
            return cache;
        }

        // ОЧИЩАЕМ СТАРЫЕ ЗАПИСИ ЕСЛИ КЭШ ПЕРЕПОЛНЕН
        if (noiseCache.size() >= MAX_CACHE_SIZE) {
            long oldestTime = Long.MAX_VALUE;
            Long oldestKey = null;

            for (Map.Entry<Long, ChunkNoiseCache> entry : noiseCache.entrySet()) {
                if (entry.getValue().lastAccessTime < oldestTime) {
                    oldestTime = entry.getValue().lastAccessTime;
                    oldestKey = entry.getKey();
                }
            }

            if (oldestKey != null) {
                noiseCache.remove(oldestKey);
            }
        }

        // СОЗДАЁМ НОВЫЙ КЭШ
        cache = new ChunkNoiseCache(chunkX, chunkZ, featureNoise, terrainNoise);
        noiseCache.put(key, cache);
        return cache;
    }

    /**
     * ★ МЕТОД: Старение и декор (С защитой фундамента) ★
     * 1. Нижние 5 блоков от пола абсолютно монолитны (никаких дыр и трещин)
     * 2. Ширина динамическая (2-5 блоков)
     * 3. Длина ограничена (обрезается маской, превращаясь в короткие сегменты)
     * 4. Глубина строго ограничена (1-3 блока, не пробивает насквозь)
     */
    private BlockState getDecayedWallBlock(int x, int y, int z, int depthFromSurface) {

        // ★ АБСОЛЮТНАЯ ЗАЩИТА ФУНДАМЕНТА ★
        // Нижние 5 блоков от пола (y от FLOOR_Y до FLOOR_Y + 4) остаются идеально целыми.
        // Здесь не будет ни трещин, ни дыр, ни выветривания.
        if (y < FLOOR_Y + 5) {
            return getWallBlock(x, y, z);
        }

        // Абсолютная защита: если расстояние от поверхности больше 3 блоков, возвращаем целую стену
        if (depthFromSurface > 3) {
            return getWallBlock(x, y, z);
        }

        // ==========================================
        // 1. Базовая форма трещин (трубчатая) и динамическая ширина (2-5 блоков)
        // ==========================================
        // Частота 0.04 (период 25), подходит для генерации широких трещин
        double coreNoise = Math.abs(featureNoise.noise(x * 0.04, y * 0.04, z * 0.04));

        // Используем низкочастотный шум для плавного изменения ширины трещины
        double widthMod = terrainNoise.noise(x * 0.015, y * 0.015, z * 0.015);
        // Маппинг порога от 0.10 (~2 блока) до 0.25 (~5 блоков)
        double threshold = 0.10 + (widthMod + 1.0) * 0.075;

        boolean isCrackCore = (coreNoise < threshold);

        // ==========================================
        // 2. Обрезка трещин (ограничение длины, превращение в короткие сегменты)
        // ==========================================
        // Среднечастотный шум как "маска": трещина видна только когда значение > 0.
        // Это разрезает непрерывные трубы на короткие сегменты по 10-15 блоков.
        double breakNoise = featureNoise.noise(x * 0.08 + 500.0, y * 0.08, z * 0.08);
        boolean isVisible = (breakNoise > 0.0);

        // ==========================================
        // 3. Генерация трещин и арматуры
        // ==========================================
        if (isCrackCore && isVisible) {
            // Динамический расчет глубины текущей трещины (от 1 до 3 блоков)
            double depthMod = terrainNoise.noise(x * 0.1, y * 0.1, z * 0.1);
            int maxDepth = 1 + (int)((depthMod + 1.0) * 1.0); // Результат 1, 2, или 3

            // Блок вырезается (становится воздухом), только если его глубина меньше макс. глубины трещины
            if (depthFromSurface < maxDepth) {

                // ★ Логика арматуры (срабатывает только на поверхности depth 0) ★
                if (depthFromSurface == 0) {
                    double rebarChance = featureNoise.noise(x * 0.25, y * 0.25, z * 0.25);
                    // ~12.5% поверхностных блоков трещины будут содержать арматуру
                    if (rebarChance > 0.75) {
                        return Blocks.IRON_BARS.defaultBlockState();
                    }
                }

                return Blocks.AIR.defaultBlockState();
            }
        }

        // ==========================================
        // 4. Поверхностное выветривание (только для внешнего слоя depth 0)
        // ==========================================
        if (depthFromSurface == 0) {
            long hash = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ seed;
            int randVal = (int)(hash & 0xFF);

            if (randVal < 38) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState(); // ~15%
            if (randVal < 63) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();  // ~10%
            if (randVal < 76) return Blocks.COBBLESTONE.defaultBlockState();         // ~5%
        }

        // По умолчанию возвращаем стандартную стену
        return getWallBlock(x, y, z);
    }
    /**
     * ★ СОЗДАНИЕ ОСЕВОГО ПРОХОДА ★
     * Генерирует прямой проход вдоль оси X или Z, пробивая все стены на своем пути.
     * Ширина прохода 7 блоков (как в секторах), чтобы обеспечить плавный переход.
     */
    private void createAxisPassage(int centerX, int centerZ, int dirX, int dirZ, int length) {
        int halfWidth = 6; // Ширина 7 блоков (от -3 до 3)

        for (int step = 0; step <= length; step++) {
            int px = centerX + dirX * step;
            int pz = centerZ + dirZ * step;

            for (int w = -halfWidth; w <= halfWidth; w++) {
                int wx, wz;
                if (dirX != 0) { // Движемся вдоль оси X
                    wx = px;
                    wz = pz + w;
                } else { // Движемся вдоль оси Z
                    wx = px + w;
                    wz = pz;
                }

                long h = hash(wx, wz);
                passages.add(h);

                // ★ ГАРАНТИРОВАННО очищаем от стен ★
                mazeWalls.remove(h);
                sectorWalls.remove(h);

                // ★ Добавляем в коридоры ★
                mazeCorridors.add(h);
                sectorCorridors.add(h);
            }
        }
    }
    /**
     * ★ ПРОВЕРКА: ЯВЛЯЕТСЯ ЛИ БЛОК СТENOЙ ★
     * Используется для определения границ стен без вызова тяжелых методов генерации.
     */
    private boolean isWallBlock(int x, int z) {
        long h = hash(x, z);
        int dist = Math.max(Math.abs(x), Math.abs(z));

        // 1. Зона основного лабиринта
        if (dist > GLADE_WALL_END && dist < MAIN_MAZE_END) {
            if (mazeWalls.contains(h)) return true;
            // Буферные зоны (где нет коридоров и проходов) тоже считаются стенами
            if (!mazeCorridors.contains(h) && !passages.contains(h) && !passageZones.contains(h)) return true;
        }

        // 2. Зона секторов
        if (dist >= MAIN_MAZE_END && dist <= SECTORS_END) {
            if (!sectorCorridors.contains(h) && !passages.contains(h) && !passageZones.contains(h)) {
                boolean isInternalWall = (Math.abs(x) <= 2 || Math.abs(z) <= 2 || Math.abs(x - z) <= 2 || Math.abs(x + z) <= 2);
                if (dist >= SECTORS_END || isInternalWall) return true;
            }
        }

        // 3. Кольцевые стены (Глейда, Разделительная, Внешняя)
        if (dist > GLADE_RADIUS && dist <= GLADE_WALL_END) return true;
        if (dist > MAIN_MAZE_END && dist <= SEPARATOR_WALL_END) return true;
        if (dist > SECTORS_END && dist <= OUTER_WALL_END) return true;

        return false;
    }

    /**
     * ★ ПРОВЕРКА: ПРИМЫКАЕТ ЛИ БЛОК К СТЕНЕ ★
     */
    private boolean isAdjacentToWall(int x, int z) {
        return isWallBlock(x + 1, z) || isWallBlock(x - 1, z) ||
                isWallBlock(x, z + 1) || isWallBlock(x, z - 1);
    }



    /**
     * ★ ПРОВЕРКА ЛОГИЧЕСКОЙ СТЕНЫ ★
     * Определяет, является ли блок стеной, не вызывая тяжелых методов генерации.
     */


    /**
     * ★ ГЕНЕРАЦИЯ ОРГАНИЧНЫХ КУСТОВ (УВЕЛИЧЕННЫЕ И БОЛЕЕ ЧАСТЫЕ) ★
     */
    private BlockState tryGenerateBush(int x, int y, int z) {
        int height = y - FLOOR_Y;
        // ★ УВЕЛИЧЕНО: Кусты теперь растут на 1, 2, 3 и 4 блоках от пола
        if (height < 1 || height > 4) return null;

        int depth = 3;
        int sideCoord = 0;
        boolean isXWall = false;

        // 1. Ищем ближайшую стену и определяем локальные координаты
        for (int d = 1; d <= 2; d++) {
            if (isLogicalWall(x + d, z)) { depth = d; sideCoord = z; isXWall = true; break; }
            if (isLogicalWall(x - d, z)) { depth = d; sideCoord = z; isXWall = true; break; }
            if (isLogicalWall(x, z + d)) { depth = d; sideCoord = x; isXWall = false; break; }
            if (isLogicalWall(x, z - d)) { depth = d; sideCoord = x; isXWall = false; break; }
        }
        if (depth > 2) return null; // Слишком далеко от стены

        // ★ ЧАЩЕ: Интервал группировки уменьшен с 6.0 до 5.0 блоков
        int centerSide = (int)(Math.round(sideCoord / 5.0) * 5.0);
        double centerNoise = terrainNoise.noise(centerSide * 0.3, centerSide * 0.1, 0);
        // ★ ЧАЩЕ: Порог изменен, теперь только ~35% центров пустые (кусты растут плотнее)
        if (centerNoise < -0.3) return null;

        int offsetSide = sideCoord - centerSide;
        // ★ УВЕЛИЧЕНО: Лимит разрастания в стороны от центра
        if (Math.abs(offsetSide) > 4) return null;

        // 3. ★ МАГИЯ ОРГАНИЧЕСКОЙ ФОРМЫ (Увеличенный эллипсоид) ★
        // Нормализуем координаты в радиусы эллипсоида (4 по бокам, 4 ввысь, 3 вглубь)
        double sideNorm = (offsetSide * offsetSide) / 16.0;
        double heightNorm = (height * height) / 16.0;
        double depthNorm = (depth * depth) / 9.0;

        double dist = Math.sqrt(sideNorm + heightNorm + depthNorm);

        // 3D шум "взъерошивает" границы эллипсоида, убирая геометрическую правильность
        double perturb = featureNoise.noise(x * 0.5, y * 0.5, z * 0.5) * 0.25;

        // Если точка внутри деформированного эллипсоида — генерируем листву
        if (dist + perturb < 0.85) {
            long hash = (x * 123L) ^ (y * 456L) ^ (z * 789L) ^ seed;
            // На самой верхушке (height == 4) 15% шанс заменить на цветущую азалию
            if (height == 4 && (hash & 0xFF) < 38) {
                return Blocks.FLOWERING_AZALEA.defaultBlockState();
            }
            return getLeafBlock();
        }
        return null;
    }

    /**
     * ★ ПОЛУЧЕНИЕ БЛОКА ЛИСТВЫ ★
     * Делает листву "постоянной" (PERSISTENT), чтобы она не облетала без бревен.
     */
    private BlockState getLeafBlock() {
        try {
            return Blocks.OAK_LEAVES.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true
            );
        } catch (Exception e) {
            return Blocks.OAK_LEAVES.defaultBlockState();
        }
    }
    private BlockState generateNaturalTerrain(int x, int y, int z, int dist) {
        if (dist <= GLADE_RADIUS) {
            // ===== ГЛЕЙД =====
            double noise = terrainNoise.noise(x * 0.04, 0, z * 0.04);
            int terrainHeight = FLOOR_Y + (int)(noise * 5); // Амплитуда 5 блоков

            if (y > terrainHeight) return Blocks.AIR.defaultBlockState();
            if (y == terrainHeight) return GLADE_TOP;
            if (y > terrainHeight - 4) return GLADE_UNDER;

            // ★ КАМЕНЬ И РУДЫ ЗАПОЛНЯЮТ ВСЕ ПУСТОТЫ ПОД ЗЕМЛЕЙ ★
            return generateUnderground(x, y, z);
        } else {
            // ===== ПУСТЫНЯ =====
            double duneNoise = featureNoise.noise(x * 0.015, 0, z * 0.015);
            int desertHeight = FLOOR_Y + (int)(duneNoise * 12); // Амплитуда 12 блоков для дюн

            if (y > desertHeight) return Blocks.AIR.defaultBlockState();
            if (y == desertHeight) return Blocks.SAND.defaultBlockState();
            if (y > desertHeight - 5) return Blocks.SAND.defaultBlockState();
            if (y > desertHeight - 20) return Blocks.SANDSTONE.defaultBlockState();

            // ★ КАМЕНЬ И РУДЫ ЗАПОЛНЯЮТ ВСЕ ПУСТОТЫ ПОД ЗЕМЛЕЙ ★
            return generateUnderground(x, y, z);
        }
    }

    private BlockState generateLabyrinth(int x, int y, int z, int dist) {
        long hash = hash(x, z);
        int topY = getTopY(x, z); // ★ Получаем высоту крыши для текущей колонки

        BlockState state = Blocks.AIR.defaultBlockState();

        // ===== 2. СТЕНА ГЛЕЙДА =====
        if (dist <= GLADE_WALL_END) {
            if (gladeExits.contains(hash) && y < FLOOR_Y + GLADE_WALL_HEIGHT) {
                state = Blocks.AIR.defaultBlockState();
            } else if (y >= FLOOR_Y && y < FLOOR_Y + GLADE_WALL_HEIGHT) {
                int depthFromSurface;
                if (dist <= GLADE_RADIUS + 3) depthFromSurface = dist - (GLADE_RADIUS + 1);
                else depthFromSurface = GLADE_WALL_END - dist;
                int yDepth = (FLOOR_Y + GLADE_WALL_HEIGHT) - y;
                depthFromSurface = Math.min(depthFromSurface, yDepth);
                state = getDecayedWallBlock(x, y, z, depthFromSurface);
            }
        }
        // ===== 3. ОСНОВНОЙ ЛАБИРИНТ =====
        else if (dist < MAIN_MAZE_END) {
            state = generateMainMazeBlock(x, y, z, hash);
        }
        // ===== 4. РАЗДЕЛИТЕЛЬНАЯ СТЕНА =====
        else if (dist <= SEPARATOR_WALL_END) {
            if (passages.contains(hash) && y < FLOOR_Y + SEPARATOR_WALL_HEIGHT) {
                state = Blocks.AIR.defaultBlockState();
            } else if (y >= FLOOR_Y && y < FLOOR_Y + SEPARATOR_WALL_HEIGHT) {
                int depthFromSurface;
                if (dist <= MAIN_MAZE_END + 3) depthFromSurface = dist - (MAIN_MAZE_END + 1);
                else depthFromSurface = SEPARATOR_WALL_END - dist;
                int yDepth = (FLOOR_Y + SEPARATOR_WALL_HEIGHT) - y;
                depthFromSurface = Math.min(depthFromSurface, yDepth);
                state = getDecayedWallBlock(x, y, z, depthFromSurface);
            }
        }
        // ===== 5. СЕКТОРА =====
        else if (dist <= SECTORS_END) {
            state = generateSectorBlock(x, y, z, hash);
        }
        // ===== 6. ВНЕШНЯЯ СТЕНА =====
        else if (dist <= OUTER_WALL_END) {
            if (y >= FLOOR_Y && y < FLOOR_Y + OUTER_WALL_HEIGHT) {
                int depthFromSurface;
                if (dist <= SECTORS_END + 3) depthFromSurface = dist - (SECTORS_END + 1);
                else depthFromSurface = OUTER_WALL_END - dist;
                int yDepth = (FLOOR_Y + OUTER_WALL_HEIGHT) - y;
                depthFromSurface = Math.min(depthFromSurface, yDepth);
                state = getDecayedWallBlock(x, y, z, depthFromSurface);
            }
        }


        return state;
    }
    private BlockState generateMainMazeBlock(int x, int y, int z, long hash) {
        if (y == FLOOR_Y) return randomFloorBlock();
        if (y > FLOOR_Y && y <= FLOOR_Y + MAZE_HEIGHT) {
            if (passages.contains(hash) || passageZones.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                return Blocks.AIR.defaultBlockState();
            }
            if (mazeCorridors.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                return Blocks.AIR.defaultBlockState();
            }
            int modX = Math.floorMod(x, 5);
            int modZ = Math.floorMod(z, 5);
            int depthXZ = Math.min(modX, 4 - modX);
            int depthY = (FLOOR_Y + MAZE_HEIGHT) - y;
            int depthFromSurface = Math.min(depthXZ, depthY);
            return getDecayedWallBlock(x, y, z, depthFromSurface);
        }
        return Blocks.AIR.defaultBlockState();
    }

    /**
     * Гарантированно очищает всю ячейку 5x5, к которой принадлежит блок.
     * Решает проблему "замкнутых кусков 5 на 5" в проходах.
     */
    private void addPassageBlock(int x, int z) {
        // Выравнивание по сетке 5x5 (работает корректно с отрицательными координатами)
        int baseX = Math.floorDiv(x, 5) * 5;
        int baseZ = Math.floorDiv(z, 5) * 5;

        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                long h = hash(baseX + dx, baseZ + dz);
                passages.add(h);
                mazeWalls.remove(h);
                sectorWalls.remove(h);
                mazeCorridors.add(h); // Помечаем как коридор для консистентности
            }
        }
    }


    private BlockState getWallBlock(int x, int y, int z) {
        long mixed = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ seed;
        mixed ^= (mixed >>> 32);
        mixed *= 0x85EBCA77C2B2AE63L;
        mixed ^= (mixed >>> 27);
        mixed *= 0xC2B2AE3D27D4EB4FL;
        mixed ^= (mixed >>> 31);

        int index = (int)(mixed & 0x7FFFFFFF) % 3;
        return WALL_BLOCKS[Math.abs(index)];
    }

    private BlockState randomFloorBlock() {
        return Math.random() < 0.5 ? FLOOR_ANDESITE : FLOOR_POLISHED_ANDESITE;
    }

    @Override
    public void addDebugScreenInfo(@NotNull List<String> list, @NotNull RandomState random, @NotNull BlockPos pos) {
        int dist = Math.max(Math.abs(pos.getX()), Math.abs(pos.getZ()));
        long hash = hash(pos.getX(), pos.getZ());
        list.add("Labyrinth Generator");
        list.add("Dist: " + dist);
        list.add("Glade exit: " + gladeExits.contains(hash));
        list.add("Passage: " + passages.contains(hash));
        list.add("Passage zone: " + passageZones.contains(hash));
        list.add("Maze corridor: " + mazeCorridors.contains(hash));
        list.add("Maze wall: " + mazeWalls.contains(hash));
    }

    @Override
    public int getSeaLevel() { return 63; }
    @Override
    public int getMinY() { return -64; }
    @Override
    public int getGenDepth() { return 384; }

    @Override
    public int getBaseHeight(int x, int z, @NotNull Heightmap.Types type,
                             @NotNull LevelHeightAccessor level, @NotNull RandomState random) {
        return FLOOR_Y;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor level, @NotNull RandomState random) {
        return new NoiseColumn(level.getMinBuildHeight(), new BlockState[level.getHeight()]);
    }

    /**
     * ★ ВЫЧИСЛЕНИЕ ВЫСОТЫ СТЕНЫ ДЛЯ ТЕКУЩЕЙ КОЛОНКИ ★
     */
    private int getTopY(int x, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist <= GLADE_WALL_END) return FLOOR_Y + GLADE_WALL_HEIGHT;
        if (dist <= SEPARATOR_WALL_END) return FLOOR_Y + SEPARATOR_WALL_HEIGHT;
        if (dist <= OUTER_WALL_END) return FLOOR_Y + OUTER_WALL_HEIGHT;

        boolean isInternalWall = (Math.abs(x) <= 2 || Math.abs(z) <= 2 || Math.abs(x - z) <= 2 || Math.abs(x + z) <= 2);
        if (dist <= SECTORS_END && isInternalWall) return FLOOR_Y + SEPARATOR_WALL_HEIGHT;

        return FLOOR_Y + MAZE_HEIGHT;
    }

    /**
     * ★ ПРОВЕРКА: НАХОДИТСЯ ЛИ БЛОК ВНУТРИ СПЛОШНОЙ СТЕНЫ ★
     */
    private boolean isSolidWall(int x, int y, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        int topY = getTopY(x, z);
        if (y > topY) return false; // Над стеной

        if (dist <= GLADE_WALL_END) return true;
        if (dist <= SEPARATOR_WALL_END) return true;
        if (dist <= OUTER_WALL_END) return true;

        if (dist < MAIN_MAZE_END) {
            long h = hash(x, z);
            if (passages.contains(h) || passageZones.contains(h)) return false;
            if (mazeCorridors.contains(h)) return false;
            return true;
        }

        if (dist <= SECTORS_END) {
            long h = hash(x, z);
            if (passages.contains(h) || passageZones.contains(h)) return false;
            if (sectorCorridors.contains(h)) return false;
            return true;
        }

        return false;
    }

    /**
     * ★ ГЕНЕРАЦИЯ ТОЛСТЫХ ЛИАН (ТРОПИЧЕСКАЯ ЛИСТЬЯ) ★
     * Лежат на крышах стен и свисают дугами в проходы.
     */
    /**
     * ★ ПРОВЕРКА: ЯВЛЯЕТСЯ ЛИ БЛОК ЛОГИЧЕСКОЙ СТЕНОЙ ★
     */
    private boolean isWallAt(int x, int z) {
        long h = hash(x, z);
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist <= GLADE_RADIUS || dist > OUTER_WALL_END) return false; // Природный ландшафт
        if (passages.contains(h) || passageZones.contains(h)) return false;
        if (mazeCorridors.contains(h) || sectorCorridors.contains(h)) return false;
        return true; // Всё остальное в зонах лабиринта — стена
    }

    /**
     * ★ ГЕНЕРАЦИЯ ГУСТЫХ ЛИАН, ПАДАЮЩИХ ДУГОЙ СО СТЕН ★
     * Лианы жестко привязаны к поверхности стены и свисают в проход.
     */
    /**
     * ★ 寻找最近的墙壁及其顶部高度 ★
     * 返回数组: {距离, 墙顶Y坐标, 墙壁方向(0=X+, 1=X-, 2=Z+, 3=Z-)}
     */
    private int[] findNearestWall(int x, int z) {
        for (int d = 1; d <= 4; d++) { // 最大影响半径 4 格，彻底杜绝大立方体
            if (isLogicalWall(x + d, z)) return new int[]{d, getTopY(x + d, z), 0};
            if (isLogicalWall(x - d, z)) return new int[]{d, getTopY(x - d, z), 1};
            if (isLogicalWall(x, z + d)) return new int[]{d, getTopY(x, z + d), 2};
            if (isLogicalWall(x, z - d)) return new int[]{d, getTopY(x, z - d), 3};
            // 对角线检查（适配扇区的斜墙）
            if (isLogicalWall(x + d, z + d)) return new int[]{d, getTopY(x + d, z + d), 0};
            if (isLogicalWall(x - d, z - d)) return new int[]{d, getTopY(x - d, z - d), 1};
            if (isLogicalWall(x + d, z - d)) return new int[]{d, getTopY(x + d, z - d), 0};
            if (isLogicalWall(x - d, z + d)) return new int[]{d, getTopY(x - d, z + d), 1};
        }
        return null; // 周围没有墙，绝对不生成
    }




    // КЭШ ДЛЯ СТОЛБЦОВ (X, Z)
    private static final Map<Long, Boolean> lianaCache = new ConcurrentHashMap<>();

    // ★ ОПТИМИЗАЦИЯ ЛИАН (УБИРАЕМ ОПАСНЫЙ КЭШ, УБИВАЮЩИЙ TPS) ★
    private boolean isLianaRope(int x, int z) {
        // ★ УБРАН lianaCache (ConcurrentHashMap).
        // Шум детерминирован, вычисляется за наносекунды. Кэш создавал пробки в многопоточке.
        double noise1 = featureNoise.noise(x * 0.1, 0, z * 0.1);
        double noise2 = terrainNoise.noise(x * 0.05, 0, z * 0.05);
        return noise1 > 0.6 && noise2 > 0.3;
    }
    /**
     * ★ 根据墙壁方向生成侧面贴合的原版藤蔓 ★
     */
    private BlockState getVineBlockFacingWall(int wallDir) {
        switch (wallDir) {
            case 0: return getVineBlock(false, false, false, true, false);  // 墙在 X+，藤蔓朝 EAST
            case 1: return getVineBlock(false, false, false, false, true);  // 墙在 X-，藤蔓朝 WEST
            case 2: return getVineBlock(false, false, true, false, false);  // 墙在 Z+，藤蔓朝 SOUTH
            case 3: return getVineBlock(false, true, false, false, false);  // 墙在 Z-，藤蔓朝 NORTH
            default: return getVineBlock(true, false, false, false, false);
        }
    }

    // ==========================================
    // 以下为基础辅助方法（确保它们存在且正确）
    // ==========================================

    private boolean isLogicalWall(int x, int z) {
        long h = hash(x, z);
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist <= GLADE_RADIUS || dist > OUTER_WALL_END) return false;
        if (passages.contains(h) || passageZones.contains(h)) return false;
        if (mazeCorridors.contains(h) || sectorCorridors.contains(h)) return false;
        return true;
    }



    private BlockState getJungleLeafBlock() {
        try {
            return Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true
            );
        } catch (Exception e) {
            return Blocks.JUNGLE_LEAVES.defaultBlockState();
        }
    }

    private BlockState getVineBlock(boolean up, boolean north, boolean south, boolean east, boolean west) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        try {
            if (up) vine = vine.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.UP, true);
            if (north) vine = vine.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, true);
            if (south) vine = vine.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, true);
            if (east) vine = vine.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST, true);
            if (west) vine = vine.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, true);
        } catch(Exception e) {}
        return vine;
    }

    /**
     * ★ ГЕНЕРАЦИЯ ВАНИЛЬНЫХ ЛИАН (VINE) НА ТРОПИЧЕСКОЙ ЛИСТВЕ ★
     */


    // Вспомогательный метод для проверки, является ли блок листвой
    private boolean isLeaf(BlockState state) {
        return state != null && state.getBlock() == Blocks.JUNGLE_LEAVES;
    }

    /**
     * ★ ПОЛУЧЕНИЕ БЛОКА ТРОПИЧЕСКОЙ ЛИСТВЫ ★
     */

    private void validateAndFixRealPassages() {
        int fixedPassages = 0;
        int fixedMaze = 0;
        int fixedSectors = 0;

        // 1. Жесткая проверка проходов: они НИКОГДА не должны пересекаться со стенами
        for (long h : passages) {
            if (mazeWalls.remove(h)) fixedPassages++;
            if (sectorWalls.remove(h)) fixedPassages++;
        }
        for (long h : passageZones) {
            if (mazeWalls.remove(h)) fixedPassages++;
            if (sectorWalls.remove(h)) fixedPassages++;
        }

        // 2. Поиск пересечений в основном лабиринте (Коридоры ∩ Стены)
        Set<Long> mazeConflicts = new HashSet<>(mazeCorridors);
        mazeConflicts.retainAll(mazeWalls); // Оставляем только те, что есть в обоих множествах
        if (!mazeConflicts.isEmpty()) {
            fixedMaze = mazeConflicts.size();
            mazeWalls.removeAll(mazeConflicts); // Принудительно убираем стены, освобождая путь
        }

        // 3. Поиск пересечений в секторах
        Set<Long> sectorConflicts = new HashSet<>(sectorCorridors);
        sectorConflicts.retainAll(sectorWalls);
        if (!sectorConflicts.isEmpty()) {
            fixedSectors = sectorConflicts.size();
            sectorWalls.removeAll(sectorConflicts);
        }

        // 4. Логирование результатов в консоль
        if (fixedPassages > 0 || fixedMaze > 0 || fixedSectors > 0) {
            System.out.println("[LabyrinthMod] ⚠️ Обнаружены и исправлены «замурованные» проходы:");
            if (fixedPassages > 0) System.out.println("   - Проходы (Passages) блокировались стенами: " + fixedPassages + " блоков");
            if (fixedMaze > 0) System.out.println("   - Конфликты в основном лабиринте: " + fixedMaze + " блоков");
            if (fixedSectors > 0) System.out.println("   - Конфликты в секторах: " + fixedSectors + " блоков");
        } else {
            System.out.println("[LabyrinthMod] ✅ Проверка целостности проходов пройдена. Конфликтов не найдено.");
        }
    }

}