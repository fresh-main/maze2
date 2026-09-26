package com.labyrinthmod.common.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.util.RandomSource;

public class LabyrinthChunkGenerator extends ChunkGenerator {
    private static volatile LabyrinthChunkGenerator activeGenerator;
    public static final Codec<LabyrinthChunkGenerator> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(LabyrinthChunkGenerator::getBiomeSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed)  // ★ 0 = "брать сид из мира"
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
    private int GLADE_WALL_HEIGHT = 61;
    private int SEPARATOR_WALL_HEIGHT = 71;
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

    private volatile boolean isGenerated = false;
    private final Object generationLock = new Object();



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

    private volatile long seed;
    private volatile boolean seedInitialized = false;

    // ★ КЭШ ЛАБИРИНТА ★
    private final Set<Long> mazeCorridors = new HashSet<>();
    private final Set<Long> mazeWalls = new HashSet<>();
    private final Set<Long> sectorCorridors = new HashSet<>();
    private final Set<Long> sectorWalls = new HashSet<>();
    private final Set<Long> gladeExits = new HashSet<>();
    private final Set<Long> passages = new HashSet<>(); // ★ ВСЕ ПРОХОДЫ ★
    private final Set<Long> passageZones = new HashSet<>();// ★ РАСШИРЕННЫЕ ЗОНЫ ★
    /** Local, axis-aligned wall moves which define variant B. */
    private final List<ShiftDefinition> plannedShifts = new ArrayList<>();
    private static final int MAX_SHIFT_ZONES = 40;
    private static final int MIN_SECTIONS_PER_ZONE = 4;
    private static final int MAX_SECTIONS_PER_ZONE = 10;
    private volatile ImprovedNoise terrainNoise;
    private volatile ImprovedNoise featureNoise;
    private final LabyrinthConfig config;
    private final Set<Long> movingWallCells = new HashSet<>();



    public LabyrinthChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed = seed;
        activeGenerator = this;

        LabyrinthConfig cfg = LabyrinthConfig.getInstance();
        this.config = cfg;

        this.GLADE_RADIUS = cfg.gleydRadius;
        this.MAIN_MAZE_WIDTH = cfg.mainMazeWidth * 10;
        this.SECTOR_WIDTH = cfg.sectorWidth * 12;
        this.MAZE_HEIGHT = cfg.mainMazeHeight;
        this.CELL_SIZE = CORRIDOR_WIDTH + WALL_THICKNESS;
        this.GLADE_WALL_HEIGHT = MAZE_HEIGHT + 10;
        this.SEPARATOR_WALL_HEIGHT = MAZE_HEIGHT + 20;
        this.OUTER_WALL_HEIGHT = MAZE_HEIGHT + 50;

        this.GLADE_WALL_END = GLADE_RADIUS + GLADE_WALL_THICKNESS;
        this.MAIN_MAZE_END = GLADE_RADIUS + MAIN_MAZE_WIDTH;
        this.SEPARATOR_WALL_END = MAIN_MAZE_END + SEPARATOR_WALL_THICKNESS;
        this.SECTORS_END = SEPARATOR_WALL_END + SECTOR_WIDTH;
        this.OUTER_WALL_END = SECTORS_END + OUTER_WALL_THICKNESS;
        this.SECTOR_ENTRANCE_DISTANCE = GLADE_WALL_END + 50;

        this.PASSAGE_DISTANCE = SEPARATOR_WALL_END;
        this.PASSAGE_OFFSET = MAIN_MAZE_WIDTH;

        if (seed != 0) {
            this.terrainNoise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(seed));
            this.featureNoise = new ImprovedNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(seed ^ 0x123456789ABCDEFL));
            this.seedInitialized = true;
        } else {
            this.terrainNoise = null;
            this.featureNoise = null;
            this.seedInitialized = false;
        }
    }

    /** Uses the exact river curve and width used by terrain generation. */
    public static boolean isGeneratedRiverAt(int x, int z) {
        LabyrinthChunkGenerator generator = activeGenerator;
        return generator != null && generator.isInRiverZone(x, z);
    }

    public static boolean hasActiveGenerator() {
        return activeGenerator != null;
    }

    /** The same area in which the decoration pass places the forest. */
    public static boolean isGeneratedForestAt(int x, int z) {
        LabyrinthChunkGenerator generator = activeGenerator;
        if (generator == null) return false;
        if (z >= -30) return false;
        if (Math.sqrt((double) x * x + (double) z * z) < 20.0) return false;
        if (Math.max(Math.abs(x), Math.abs(z)) > generator.GLADE_RADIUS - 5) return false;
        return !generator.isInRiverZone(x, z);
    }

    private void generateMaze() {
        mazeCorridors.clear();
        mazeWalls.clear();
        gladeExits.clear();
        plannedShifts.clear();

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

        planLocalVariantBShifts(grid, isValid, CENTER);

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

    private void planLocalVariantBShifts(boolean[][] grid, boolean[][] isValid, int center) {
        for (ShiftDefinition shift : plannedShifts) {
            for (int dx = 0; dx < shift.sizeX(); dx++) {
                for (int dz = 0; dz < shift.sizeZ(); dz++) {
                    movingWallCells.add(hash(shift.sourceX() + dx, shift.sourceZ() + dz));
                }
            }
        }
        int size = grid.length;
        boolean[] open = new boolean[size * size];
        boolean[] wall = new boolean[size * size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                open[i * size + j] = isValid[i][j] && !grid[i][j];
                wall[i * size + j] = isValid[i][j] && grid[i][j];
            }
        }
        BridgeIndex bridges = new BridgeIndex(open, size);

        int firstLength = MIN_SECTIONS_PER_ZONE + MIN_SECTIONS_PER_ZONE % 2;
        int lastLength = MAX_SECTIONS_PER_ZONE - MAX_SECTIONS_PER_ZONE % 2;
        List<SlideCandidate> candidates = new ArrayList<>();
        for (int axis = 0; axis < 2; axis++) {
            for (int line = 1; line + 1 < size; line += 2) {
                for (int slot = 0; slot < size; slot += 2) {
                    for (int length = firstLength; length <= lastLength; length += 2) {
                        for (int sign = 1; sign >= -1; sign -= 2) {
                            SlideCandidate candidate =
                                    buildCandidate(open, wall, bridges, size, axis, line, slot, length, sign);
                            if (candidate != null) candidates.add(candidate);
                        }
                    }
                }
            }
        }

        Random shiftRandom = new Random(seed ^ 0x6A09E667F3BCC909L);
        Collections.shuffle(candidates, shiftRandom);

        boolean[] taken = new boolean[size * size];
        List<SlideCandidate> chosen = new ArrayList<>();
        int lengthChoices = (lastLength - firstLength) / 2 + 1;
        for (int shift = 0; shift < MAX_SHIFT_ZONES; shift++) {
            int wanted = firstLength + 2 * shiftRandom.nextInt(lengthChoices);
            SlideCandidate pick = null;
            for (int pass = 0; pass < 2 && pick == null; pass++) {
                for (SlideCandidate candidate : candidates) {
                    if (pass == 0 && candidate.length() != wanted) continue;
                    if (isFree(candidate, taken, size) && isIndependent(candidate, chosen, bridges)) {
                        pick = candidate;
                        break;
                    }
                }
            }
            if (pick == null) break;

            chosen.add(pick);
            for (int t = pick.first(); t < pick.first() + pick.length(); t++) {
                taken[cellAt(pick.axis(), pick.line(), t, size)] = true;
            }
            taken[pick.lead()] = true;

            int sourceAlong = (pick.first() - center) * 5;
            int sourceLine = (pick.line() - center) * 5;
            int span = pick.length() * 5;
            if (pick.axis() == 0) {
                plannedShifts.add(new ShiftDefinition(sourceAlong, sourceLine, span, 5, pick.sign() * 5, 0));
            } else {
                plannedShifts.add(new ShiftDefinition(sourceLine, sourceAlong, 5, span, 0, pick.sign() * 5));
            }
        }
    }

    private record SlideCandidate(int axis, int line, int first, int length, int sign,
                                  int roomA, int roomB, int lead, int child) {
    }

    private static int cellAt(int axis, int line, int along, int size) {
        return axis == 0 ? along * size + line : line * size + along;
    }

    private static int roomAcross(int axis, int line, int along, int side, int size) {
        return axis == 0 ? along * size + line + side : (line + side) * size + along;
    }

    private static SlideCandidate buildCandidate(boolean[] open, boolean[] wall, BridgeIndex bridges, int size,
                                                 int axis, int line, int slot, int length, int sign) {
        int first = sign > 0 ? slot : slot - length + 1;
        int last = first + length - 1;
        int leadAlong = slot + sign * length;
        int behind = slot - sign;
        int beyond = leadAlong + sign;
        if (first < 0 || last >= size || leadAlong < 0 || leadAlong >= size) return null;

        for (int t = first; t <= last; t++) {
            if (!wall[cellAt(axis, line, t, size)]) return null;
        }
        int lead = cellAt(axis, line, leadAlong, size);
        if (!open[lead]) return null;
        if (beyond >= 0 && beyond < size && open[cellAt(axis, line, beyond, size)]) return null;
        if (behind >= 0 && behind < size && open[cellAt(axis, line, behind, size)]) return null;

        int leadA = roomAcross(axis, line, leadAlong, -1, size);
        int leadB = roomAcross(axis, line, leadAlong, 1, size);
        int roomA = roomAcross(axis, line, slot, -1, size);
        int roomB = roomAcross(axis, line, slot, 1, size);
        if (!open[leadA] || !open[leadB] || !open[roomA] || !open[roomB]) return null;

        int component = bridges.component[lead];
        if (bridges.component[roomA] != component || bridges.component[roomB] != component) return null;

        int parent = bridges.parent[lead];
        if (parent != leadA && parent != leadB) return null;
        int child = parent == leadA ? leadB : leadA;
        if (bridges.parent[child] != lead || bridges.low[child] <= bridges.tin[lead]) return null;
        if (!bridges.separates(roomA, roomB, child)) return null;

        return new SlideCandidate(axis, line, first, length, sign, roomA, roomB, lead, child);
    }

    private static boolean isFree(SlideCandidate candidate, boolean[] taken, int size) {
        for (int t = candidate.first(); t < candidate.first() + candidate.length(); t++) {
            if (taken[cellAt(candidate.axis(), candidate.line(), t, size)]) return false;
        }
        return !taken[candidate.lead()];
    }

    private static boolean isIndependent(SlideCandidate candidate, List<SlideCandidate> chosen,
                                         BridgeIndex bridges) {
        for (SlideCandidate other : chosen) {
            if (bridges.separates(other.roomA(), other.roomB(), candidate.child())) return false;
            if (bridges.separates(candidate.roomA(), candidate.roomB(), other.child())) return false;
        }
        return true;
    }

    private static final class BridgeIndex {
        final int[] tin;
        final int[] tout;
        final int[] low;
        final int[] parent;
        final int[] component;

        BridgeIndex(boolean[] open, int size) {
            int cells = open.length;
            tin = new int[cells];
            tout = new int[cells];
            low = new int[cells];
            parent = new int[cells];
            component = new int[cells];
            Arrays.fill(tin, -1);
            Arrays.fill(parent, -1);
            Arrays.fill(component, -1);

            int[] next = new int[cells];
            int[] stack = new int[cells];
            int timer = 0;
            int components = 0;
            for (int root = 0; root < cells; root++) {
                if (!open[root] || tin[root] >= 0) continue;
                int top = 0;
                stack[top++] = root;
                tin[root] = timer;
                low[root] = timer++;
                component[root] = components;
                while (top > 0) {
                    int cell = stack[top - 1];
                    if (next[cell] < 4) {
                        int neighbor = neighborOf(cell, next[cell]++, size);
                        if (neighbor < 0 || !open[neighbor] || neighbor == parent[cell]) continue;
                        if (tin[neighbor] >= 0) {
                            low[cell] = Math.min(low[cell], tin[neighbor]);
                        } else {
                            parent[neighbor] = cell;
                            component[neighbor] = components;
                            tin[neighbor] = timer;
                            low[neighbor] = timer++;
                            stack[top++] = neighbor;
                        }
                    } else {
                        top--;
                        tout[cell] = timer - 1;
                        if (parent[cell] >= 0) low[parent[cell]] = Math.min(low[parent[cell]], low[cell]);
                    }
                }
                components++;
            }
        }

        private static int neighborOf(int cell, int direction, int size) {
            int i = cell / size;
            int j = cell % size;
            switch (direction) {
                case 0: return i + 1 < size ? cell + size : -1;
                case 1: return i > 0 ? cell - size : -1;
                case 2: return j + 1 < size ? cell + 1 : -1;
                default: return j > 0 ? cell - 1 : -1;
            }
        }

        boolean inSubtree(int cell, int root) {
            return tin[cell] >= tin[root] && tin[cell] <= tout[root];
        }

        boolean separates(int a, int b, int root) {
            return inSubtree(a, root) != inSubtree(b, root);
        }
    }

    /** Creates the persistent source zones after variant A has been generated. */
    public List<com.labyrinthmod.common.data.LabyrinthShiftZone> createShiftZones() {
        ensureGenerated();
        List<com.labyrinthmod.common.data.LabyrinthShiftZone> zones = new ArrayList<>();
        for (int index = 0; index < plannedShifts.size(); index++) {
            ShiftDefinition shift = plannedShifts.get(index);
            UUID id = UUID.nameUUIDFromBytes(("labyrinth-shift:" + seed + ":" + index + ":"
                    + shift.sourceX + ":" + shift.sourceZ).getBytes(StandardCharsets.UTF_8));
            BlockPos min = new BlockPos(shift.sourceX, FLOOR_Y + 1, shift.sourceZ);
            BlockPos max = new BlockPos(shift.sourceX + shift.sizeX - 1, FLOOR_Y + MAZE_HEIGHT,
                    shift.sourceZ + shift.sizeZ - 1);
            zones.add(new com.labyrinthmod.common.data.LabyrinthShiftZone(
                    id, min, max, shift.offsetX, 0, shift.offsetZ));
        }
        return zones;
    }

    private record ShiftDefinition(int sourceX, int sourceZ, int sizeX, int sizeZ, int offsetX, int offsetZ) {
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



    private boolean isLianaRope(int x, int z) {
        /*
         * Лианы не должны появляться равномерной сеткой.
         *
         * Используем несколько уровней шума:
         *  - крупный шум создаёт большие заросшие участки;
         *  - мелкий шум разбивает их на отдельные пряди.
         *
         * Результат:
         *   ████      ███
         *   ███       ██
         *    █        █
         *
         * вместо:
         *   █ █ █ █ █ █
         *   █ █ █ █ █ █
         */

        double large = featureNoise.noise(
                x * 0.035,
                0,
                z * 0.035
        );

        double medium = featureNoise.noise(
                x * 0.09 + 173.0,
                0,
                z * 0.09 + 173.0
        );

        double small = featureNoise.noise(
                x * 0.22 + 731.0,
                0,
                z * 0.22 + 731.0
        );

        /*
         * ★ Региональный "характер" зарастания ★
         * Крупные участки мира (~71 блок) случайно, но детерминированно
         * (на основе seed мира) получают разную плотность лиан: где-то
         * густые заросли, где-то почти голый камень. Работает поверх
         * обычной шумовой кластеризации ниже, а не вместо неё —
         * поэтому даже внутри одного "густого" региона заросли всё
         * равно распределены группами, а не сплошным ковром.
         */
        double densityBias = vineDensityBias(x, z);

        /*
         * Большой шум отвечает за то, где вообще
         * могут быть заросли.
         */
        if (large < -0.05 + densityBias) {
            return false;
        }

        /*
         * Средний шум формирует отдельные группы.
         */
        if (medium < -0.25 + densityBias * 0.6) {
            return false;
        }

        /*
         * Мелкий шум не даёт стене покрываться
         * лианами полностью.
         */
        return small > -0.35 + densityBias * 0.4;
    }

    private double vineDensityBias(int x, int z) {
        int regionX = Math.floorDiv(x, 71);
        int regionZ = Math.floorDiv(z, 71);
        long h = (regionX * 2246822519L) ^ (regionZ * 3266489917L) ^ (seed * 668265263L);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        int bucket = (int) Math.floorMod(h, 5);
        switch (bucket) {
            case 0: return -0.35; // очень густо заросший участок
            case 1: return -0.15; // заросший
            default: return 0.05;  // умеренно
        }
    }

    /**
     * ★ ЛИАНЫ v5 (НЕ ЛЕТАЮТ) ★
     * Три защиты от неба:
     *  1. y <= localTopY — никогда выше крыши
     *  2. Только вплотную к стене (ортогонально, 1 блок)
     *  3. isSolidWall на ЭТОЙ же высоте — стена реально существует рядом
     */
    private BlockState tryGenerateVine(int x, int y, int z, int localTopY) {
        // ★ ЗАЩИТА 1: выше крыши — ничего ★
        if (y > localTopY) return null;
        int hang = localTopY - y;
        if (hang < 0 || hang > 20) return null;

        // ★ ЗАЩИТА 2: стена вплотную (depth=1) либо через блок воздуха
        // (depth=2) — второй случай нужен только для того, чтобы самые
        // пышные грозди могли слегка выступать наружу от стены и давать
        // лианам реальный объём, а не плоскую наклейку на грани блока.
        int dir = -1, depth = 0;
        if (isLogicalWall(x + 1, z)) { dir = 0; depth = 1; }
        else if (isLogicalWall(x - 1, z)) { dir = 1; depth = 1; }
        else if (isLogicalWall(x, z + 1)) { dir = 2; depth = 1; }
        else if (isLogicalWall(x, z - 1)) { dir = 3; depth = 1; }
        else if (isLogicalWall(x + 2, z)) { dir = 0; depth = 2; }
        else if (isLogicalWall(x - 2, z)) { dir = 1; depth = 2; }
        else if (isLogicalWall(x, z + 2)) { dir = 2; depth = 2; }
        else if (isLogicalWall(x, z - 2)) { dir = 3; depth = 2; }
        if (dir < 0) return null;

        // ★ ЗАЩИТА 3: стена существует НА ЭТОЙ ВЫСОТЕ (на своей грани) ★
        int wx = x + (dir == 0 ? depth : dir == 1 ? -depth : 0);
        int wz = z + (dir == 2 ? depth : dir == 3 ? -depth : 0);
        if (movingWallCells.contains(hash(wx, wz))) return null;
        if (!isSolidWall(wx, y, wz)) return null;

        if (!isLianaRope(x, z)) return null;

        // Шапка из листвы прямо на кромке (только вплотную к стене) —
        // дальше форму/длину целиком определяет объёмная система ниже,
        // без внешнего прямоугольного обрезания по единой длине.
        if (hang <= 1 && depth == 1) return getJungleLeafBlock();
        int t = (dir == 0 || dir == 1) ? z : x;
        if (!organicVineShape(x, y, z, dir, t, hang, depth)) return null;
        return getJungleLeafBlock();
    }

    /**
     * ★ ВЕРХУШКИ (ШАПКИ) ИЗ ЛИСТВЫ ★
     * Округлый куст на кромке стены: закрывает верх, свисает по бокам,
     * торчит на 1-2 блока вверх. Выглядит как заросшая кромка, а не плоская стена.
     */
    private BlockState tryGenerateVineCap(int x, int y, int z, int topY) {
        int dy = y - topY;
        // Только кромка: 1 блок ниже и до 2 блоков выше
        if (dy < -1 || dy > 2) return null;

        int[] wall = findNearestWall(x, z);
        if (wall == null) return null;
        int dist = wall[0];
        int dir = wall[2];
        if (dist > 2) return null;
        int wallTop = wall[1];

        int capWx = x + (dir == 0 ? dist : dir == 1 ? -dist : 0);
        int capWz = z + (dir == 2 ? dist : dir == 3 ? -dist : 0);
        if (movingWallCells.contains(hash(capWx, capWz))) return null;
        int t = (dir == 0 || dir == 1) ? z : x;
        int anchor = Math.floorDiv(t, 9) * 9 + 4;

        double gate = featureNoise.noise(anchor * 0.37 + dir * 91, 0, anchor * 0.19);
        if (gate < 0.35) return null;

        // Радиус куста 1.0..1.8 (было 1.6..2.6 — раньше соседние пятна перекрывались)
        double r = 1.0 + (featureNoise.noise(anchor * 0.53 + 7, 0, anchor * 0.31 + 7) + 1.0) * 0.4;

        int dt = t - anchor;                      // вдоль стены
        double dv = y - (wallTop + 0.5);          // по высоте (центр чуть выше кромки)
        double rad = Math.sqrt(dt * dt + dv * dv);

        // Чем дальше от стены — тем короче свисание (куст "сидит" на стене),
        // и падает резче, чем раньше, чтобы не размазываться на соседние блоки воздуха
        double allow = r - (dist - 1) * 1.1;
        double perturb = featureNoise.noise(x * 0.5, y * 0.5, z * 0.5) * 0.4;

        if (rad <= allow + perturb) {
            return getJungleLeafBlock();
        }
        return null;
    }
    private int[] findPassageSpan(int x, int z) {
        for (int d = 1; d <= 8; d++) {
            if (isLogicalWall(x - d, z)) {
                for (int e = 1; e <= 8; e++) {
                    if (isLogicalWall(x + e, z)) return new int[]{0, x - d, x + e};
                }
                break;
            }
        }
        for (int d = 1; d <= 8; d++) {
            if (isLogicalWall(x, z - d)) {
                for (int e = 1; e <= 8; e++) {
                    if (isLogicalWall(x, z + e)) return new int[]{1, z - d, z + e};
                }
                break;
            }
        }
        return null;
    }
    /**
     * ★ ЛИАНЫ ДУГОЙ ОТ СТЕНЫ К СТЕНЕ (v2 - КРИВАЯ БЕЗЬЕ) ★
     * Генерирует объемную дугу из тропической листвы, перекидывающуюся через проход.
     * Использует квадратичную кривую Безье для создания естественного провисания
     * и случайного отклонения (смещения) от 2 до 5 блоков в обе стороны.
     * Детерминирована: не рвется на границах чанков.
     */
    private BlockState tryGenerateSpanningVine(int x, int y, int z) {
        int localTop = wallTopAt(x, z);
        // Ограничиваем высоту генерации: только в зоне свисания от верха стены
        if (y < localTop - 15 || y > localTop + 2) return null;

        int[] span = findPassageSpan(x, z);
        if (span == null) return null;

        int axis = span[0];
        int lo = span[1];
        int hi = span[2];
        int width = hi - lo;

        // Не генерируем дуги в слишком узких или слишком широких проходах
        if (width < 3 || width > 16) return null;

        // T - координата ВДОЛЬ прохода, S - координата ПОПЕРЕК (ширина прохода)
        int t_coord = (axis == 0) ? z : x;
        int s_coord = (axis == 0) ? x : z;

        // ★ РАЗБИЕНИЕ НА СЕГМЕНТЫ ★
        // Каждый сегмент — это одна независимая дуга.
        // Используем floorDiv, чтобы сегменты были одинаковыми во всех чанках.
        int segLen = 12; // Базовая длина сегмента вдоль прохода
        int segStart = Math.floorDiv(t_coord, segLen) * segLen;

        // Детерминированный хэш для сегмента (гарантирует непрерывность между чанками)
        long segHash = hash(axis == 0 ? lo : segStart, axis == 0 ? segStart : lo);
        segHash ^= (segHash >>> 33);
        segHash *= 0xFF51AFD7ED558CCDL;
        segHash ^= (segHash >>> 33);

        Random segRand = new Random(segHash);

        // 1. Параметры дуги
        int actualLen = 10 + segRand.nextInt(8); // Длина дуги вдоль прохода (10-17 блоков)
        int segEnd = segStart + actualLen;

        // Если текущий блок не в пределах этого сегмента, он не относится к этой дуге
        if (t_coord < segStart || t_coord > segEnd) return null;

        // 2. ★ ОТКЛОНЕНИЕ (СМЕЩЕНИЕ) ОТ 2 ДО 5 БЛОКОВ ★
        // Смещение поперек прохода (S) и вдоль прохода (T), чтобы дуга была косой/органичной
        int offsetMagS = 2 + segRand.nextInt(4); // 2, 3, 4 или 5
        int offsetS = segRand.nextBoolean() ? offsetMagS : -offsetMagS;

        int offsetMagT = 2 + segRand.nextInt(4); // 2, 3, 4 или 5
        int offsetT = segRand.nextBoolean() ? offsetMagT : -offsetMagT;

        // 3. Провисание дуги вниз
        int sag = 2 + segRand.nextInt(4); // 2..5 блоков вниз

        // Реальная высота стен в точках крепления (с учетом ruinOffset)
        int wallTopLo, wallTopHi;
        if (axis == 0) {
            wallTopLo = wallTopAt(lo, z);
            wallTopHi = wallTopAt(hi, z);
        } else {
            wallTopLo = wallTopAt(x, lo);
            wallTopHi = wallTopAt(x, hi);
        }

        // ★ ТОЧКИ КРИВОЙ БЕЗЬЕ ★
        // P0: Крепление на стене 1
        // P2: Крепление на стене 2
        // P1: Контрольная точка (середина + случайные отклонения)
        double midS = (lo + hi) / 2.0;
        double midT = (segStart + segEnd) / 2.0;
        double midY = (wallTopLo + wallTopHi) / 2.0;

        double p1_s = midS + offsetS; // Смещение поперек
        double p1_t = midT + offsetT; // Смещение вдоль (делаем дугу диагональной)
        double p1_y = midY - sag;     // Провисание

        // Параметр t для кривой Безье (от 0.0 до 1.0) на основе позиции вдоль прохода
        double t_param = (double) (t_coord - segStart) / (segEnd - segStart);

        // Формула квадратичной кривой Безье: B(t) = (1-t)^2*P0 + 2*(1-t)*t*P1 + t^2*P2
        double u = 1.0 - t_param;
        double u2 = u * u;
        double t2 = t_param * t_param;
        double ut2 = 2.0 * u * t_param;

        // Вычисляем теоретические координаты центра лианы в этой точке
        double curveS = u2 * lo + ut2 * p1_s + t2 * hi;
        double curveY = u2 * wallTopLo + ut2 * p1_y + t2 * wallTopHi;

        // Расстояние от текущего блока до идеальной кривой
        double ds = s_coord - curveS;
        double dy = y - curveY;

        // ★ ТОЛЩИНА ЛИАНЫ ★
        // Добавляем 3D шум, чтобы края листвы были рваными и органичными
        double noise = featureNoise.noise(x * 0.5, y * 0.5, z * 0.5) * 0.35;
        double radius = 0.85 + noise;

        // Эллиптическая проверка расстояния (чуть сплюснута по вертикали)
        double distSq = (ds * ds) + (dy * dy * 1.25);

        if (distSq <= radius * radius) {
            // Возвращаем тропическую листву (она не осыпается благодаря PERSISTENT=true)
            return getJungleLeafBlock();
        }

        return null;
    }


    private BlockState generateSectorBlock(int x, int y, int z, long hash) {
        if (y == FLOOR_Y) return randomFloorBlock(x, z);
        int dist = Math.max(Math.abs(x), Math.abs(z));
        boolean isInternalWall = (Math.abs(x) <= 2 || Math.abs(z) <= 2 || Math.abs(x - z) <= 2 || Math.abs(x + z) <= 2);
        int wallHeight = applyRuinOffset((dist >= SECTORS_END || isInternalWall) ? SEPARATOR_WALL_HEIGHT : MAZE_HEIGHT, x, z);
        if (y > FLOOR_Y && y <= FLOOR_Y + wallHeight) {
            if (passages.contains(hash) || passageZones.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                BlockState vine = tryGenerateVine(x, y, z, FLOOR_Y + wallHeight);
                if (vine != null) return vine;
                return Blocks.AIR.defaultBlockState();
            }
            if (sectorCorridors.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                BlockState vine = tryGenerateVine(x, y, z, FLOOR_Y + wallHeight);
                if (vine != null) return vine;
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
    private void createGladePassage(int centerX, int centerZ, int dirX, int dirZ) {
        int halfWidth = 7; // Ширина 15 блоков (идеально совпадает с 3 клетками сетки лабиринта)

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
                             @NotNull ChunkAccess chunk, @NotNull GenerationStep.Carving step) {
    }

    @Override
    public void spawnOriginalMobs(@NotNull WorldGenRegion region) {
        // The custom generator used to suppress the vanilla chunk-generation spawn pass.
        // That also suppressed water creatures even when the generated water correctly had
        // the river biome. Mirror NoiseBasedChunkGenerator here so every newly generated
        // river chunk gets the biome's normal fish population.
        ChunkPos chunkPos = region.getCenter();
        Holder<Biome> biome = region.getBiome(chunkPos.getWorldPosition().atY(region.getMaxBuildHeight() - 1));
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), chunkPos.getMinBlockX(), chunkPos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(region, biome, chunkPos, random);
    }



    @Override
    @NotNull
    public CompletableFuture<ChunkAccess> fillFromNoise(@NotNull Executor executor, @NotNull Blender blender,
                                                        @NotNull RandomState random, @NotNull StructureManager structureManager,
                                                        @NotNull ChunkAccess chunk) {
        initializeSeed(random);
        invalidateRiverIfSeedChanged();
        ensureGenerated();

        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        for (int secY = chunk.getMinSection(); secY < chunk.getMaxSection(); secY++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(secY));
            int baseY = secY << 4;

            for (int localX = 0; localX < 16; localX++) {
                int worldX = (chunkX << 4) + localX;

                for (int localZ = 0; localZ < 16; localZ++) {
                    int worldZ = (chunkZ << 4) + localZ;
                    fillColumnFast(section, baseY, localX, localZ, worldX, worldZ);
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    private void fillColumnFast(LevelChunkSection section, int baseY, int localX, int localZ, int worldX, int worldZ) {
        int dist = Math.max(Math.abs(worldX), Math.abs(worldZ));
        long hash = hash(worldX, worldZ);
        int topY = getTopY(worldX, worldZ);
        boolean isNatural = (dist <= GLADE_RADIUS || dist > OUTER_WALL_END);


        double riverDist = -1;
        if (isNatural && dist <= GLADE_RADIUS) {
            riverDist = distanceToRiverCurve(worldX, worldZ);
        }

        boolean nearGladeWall = dist <= GLADE_RADIUS && dist >= GLADE_RADIUS - 4;
        boolean nearOuterWall = dist > OUTER_WALL_END && dist <= OUTER_WALL_END + 4;

        for (int localY = 0; localY < 16; localY++) {
            int worldY = baseY + localY;
            BlockState state;
            if (isNatural) {
                state = generateNaturalTerrain(worldX, worldY, worldZ, dist, riverDist);
                if ((nearGladeWall || nearOuterWall) && (state == null || state.isAir())) {
                    BlockState vines = tryGenerateWallVines(worldX, worldY, worldZ);
                    if (vines != null) state = vines;
                }
            } else {
                if (worldY < FLOOR_Y) {
                    state = getWallBlock(worldX, worldY, worldZ);
                } else {
                    state = generateLabyrinthFast(worldX, worldY, worldZ, dist, hash, topY);
                }
            }
            if (state == null) state = Blocks.AIR.defaultBlockState();
            section.setBlockState(localX, localY, localZ, state, false);
        }
    }

    private BlockState generateLabyrinthFast(int x, int y, int z, int dist, long hash, int topY) {
        boolean isMovingWall = movingWallCells.contains(hash);
        if (y > topY) {
            // ★ ЛИСТВА ТОРЧИТ НАД КРОМКОЙ ★
            if (!isMovingWall) {
                BlockState vines = tryGenerateWallVines(x, y, z);
                if (vines != null) return vines;
            }
            return Blocks.AIR.defaultBlockState();
        }

        // ★ ЛИАНЫ И КУСТЫ — до всех зон (кроме движущихся стен) ★
        if (!isMovingWall) {
            BlockState vines = tryGenerateWallVines(x, y, z);
            if (vines != null) return vines;
        }

        BlockState state = Blocks.AIR.defaultBlockState();

        if (dist <= GLADE_WALL_END) {
            // ★ ПРОХОД ПОДНЯТ НА 1 БЛОК: y >= FLOOR_Y + 1 ★
            if (gladeExits.contains(hash) && y >= FLOOR_Y + 1 && y < FLOOR_Y + GLADE_WALL_HEIGHT) {
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

        // 1. Переход в Deepslate и Tuff (Ванильная логика)
        if (y < 0) {
            double deepslateNoise = terrainNoise.noise(x * 0.05, y * 0.05, z * 0.05);
            if (y < -8) {
                return Blocks.DEEPSLATE.defaultBlockState(); // Ниже Y=-8 только глубинный сланец
            } else {
                if (deepslateNoise > 0.0) return Blocks.DEEPSLATE.defaultBlockState(); // Плавный переход
            }

            // Жилы туфа (Tuff)
            double tuffNoise = featureNoise.noise(x * 0.1, y * 0.1, z * 0.1);
            if (tuffNoise > 0.8) return Blocks.TUFF.defaultBlockState();
        }

        // 2. Карманы земли и гравия (Dirt and Gravel pockets)
        double pocketNoise = featureNoise.noise(x * 0.08, y * 0.08, z * 0.08);
        if (pocketNoise > 0.85) {
            return y < 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }

        // 3. Жилы Гранита, Диорита и Андезита
        double stoneOreNoise = terrainNoise.noise(x * 0.12 + 100, y * 0.12, z * 0.12 + 100);
        if (stoneOreNoise > 0.88) {
            long hash = (x * 31L) ^ (y * 17L) ^ (z * 13L) ^ seed;
            int type = (int)(hash & 0x3);
            if (type == 0) return Blocks.GRANITE.defaultBlockState();
            if (type == 1) return Blocks.DIORITE.defaultBlockState();
            if (type == 2) return Blocks.ANDESITE.defaultBlockState();
        }

        // 4. Обычный камень (Руды теперь генерируются ванилью через super.applyBiomeDecoration)
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

    // =====================================================================
    // ★ СИСТЕМА ТРЕЩИН v5 (ОРИГИНАЛ v3 + УМНЫЕ ПРУТЬЯ) ★
    // =====================================================================
    private static final int MAX_CARVE_DEPTH = 2;

    private int[] wallGeometry(int x, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist <= GLADE_WALL_END) {
            int dH = Math.min(dist - GLADE_RADIUS, GLADE_WALL_END - dist);
            return new int[]{Math.max(0, dH), GLADE_WALL_END - GLADE_RADIUS + 1, applyRuinOffset(GLADE_WALL_HEIGHT, x, z)};
        }
        if (dist < MAIN_MAZE_END) {
            int modX = Math.floorMod(x, 5);
            int modZ = Math.floorMod(z, 5);
            int dH = Math.min(Math.min(modX, 4 - modX), Math.min(modZ, 4 - modZ));
            return new int[]{dH, WALL_THICKNESS, applyRuinOffset(MAZE_HEIGHT, x, z)};
        }
        if (dist <= SEPARATOR_WALL_END) {
            int dH = Math.min(dist - MAIN_MAZE_END, SEPARATOR_WALL_END - dist);
            return new int[]{Math.max(0, dH), SEPARATOR_WALL_END - MAIN_MAZE_END + 1, applyRuinOffset(SEPARATOR_WALL_HEIGHT, x, z)};
        }
        if (dist <= SECTORS_END) {
            boolean internal = (Math.abs(x) <= 2 || Math.abs(z) <= 2
                    || Math.abs(x - z) <= 2 || Math.abs(x + z) <= 2);
            int dH;
            if (Math.abs(x) <= 2) dH = 2 - Math.abs(x);
            else if (Math.abs(z) <= 2) dH = 2 - Math.abs(z);
            else if (Math.abs(x - z) <= 2) dH = 2 - Math.abs(x - z);
            else if (Math.abs(x + z) <= 2) dH = 2 - Math.abs(x + z);
            else dH = SECTORS_END - dist;
            int height = applyRuinOffset((dist >= SECTORS_END || internal) ? SEPARATOR_WALL_HEIGHT : MAZE_HEIGHT, x, z);
            int thickness = internal ? 5 : 64;
            return new int[]{Math.max(0, dH), thickness, height};
        }
        int dH = Math.min(dist - SECTORS_END, OUTER_WALL_END - dist);
        return new int[]{Math.max(0, dH), OUTER_WALL_END - SECTORS_END + 1, applyRuinOffset(OUTER_WALL_HEIGHT, x, z)};
    }

    // =====================================================================
    // ★ СИСТЕМА НЕРОВНОГО СИЛУЭТА РУИН (v1) ★
    // Ломает идеально ровную, одинаковую высоту/форму стен: верх стен
    // становится неровным, ступенчатым, местами обрушенным, местами
    // "уцелевшим" (выше среднего) — как у настоящей заросшей древней
    // постройки, а не у прямоугольной коробки.
    //
    // Работает ТОЛЬКО поверх уже существующей логики (высота колонки,
    // текстура поверхности). Форма коридоров/стен в плане (topology,
    // проходимость лабиринта) не меняется — по требованию не трогать
    // остальные системы/механику без необходимости.
    // =====================================================================

    /**
     * Делит мир на крупные (~53 блока) регионы и жёстко, но случайно
     * (на основе seed мира) назначает каждому региону один из
     * нескольких "почерков" разрушения. Благодаря этому разные участки
     * одной и той же постройки выглядят по-разному, а не повторяют один
     * и тот же шаблон каждые несколько блоков.
     */
    private int ruinProfileVariant(int x, int z) {
        int regionX = Math.floorDiv(x, 53);
        int regionZ = Math.floorDiv(z, 53);
        long h = (regionX * 668265263L) ^ (regionZ * 374761393L) ^ (seed * 2654435761L);
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        return (int) Math.floorMod(h, 4); // 4 варианта "почерка" разрушения
    }

    /**
     * Множитель интенсивности выветривания/трещин для существующей
     * системы трещин (crackZone/crackVein) — разные регионы выглядят
     * более целыми или более ветхими, не меняя саму механику трещин.
     */
    private double erosionIntensity(int x, int z) {
        switch (ruinProfileVariant(x, z)) {
            case 0: return 0.70;  // более опрятный, целый участок
            case 1: return 1.00;  // обычная выветренность
            case 2: return 1.55;  // сильно ветхий, потрескавшийся участок
            default: return 1.15;
        }
    }

    private int ruinHeightOffset(int x, int z) {
        int variant = ruinProfileVariant(x, z);

        double freqBig, freqMed, freqFine, ampBig, ampMed, ampFine;
        switch (variant) {
            case 0: freqBig = 0.018; freqMed = 0.050; freqFine = 0.140; ampBig = 5.0; ampMed = 2.2; ampFine = 1.0; break;
            case 1: freqBig = 0.012; freqMed = 0.040; freqFine = 0.110; ampBig = 7.0; ampMed = 1.6; ampFine = 0.8; break;
            case 2: freqBig = 0.026; freqMed = 0.070; freqFine = 0.170; ampBig = 3.5; ampMed = 2.8; ampFine = 1.3; break;
            default: freqBig = 0.020; freqMed = 0.060; freqFine = 0.150; ampBig = 4.5; ampMed = 2.0; ampFine = 1.1; break;
        }

        double big = terrainNoise.noise(x * freqBig + 401.0, 0, z * freqBig + 401.0);
        double med = terrainNoise.noise(x * freqMed + 913.0, 0, z * freqMed + 913.0);
        double fine = featureNoise.noise(x * freqFine + 57.0, 0, z * freqFine + 57.0);

        double h = big * ampBig + med * ampMed + fine * ampFine;

        // Редкие крупные обвалы: отдельная низкочастотная маска резко
        // "вырезает" ещё несколько блоков высоты на ограниченных
        // участках — разрушенные проломы в кромке стены.
        double collapseMask = terrainNoise.noise(x * 0.02 + 8123.0, 0, z * 0.02 + 8123.0);
        if (collapseMask < -0.62) {
            double depth = (-0.62 - collapseMask) / 0.38; // 0..1
            h -= depth * 9.0;
        }

        return (int) Math.round(h);
    }

    /**
     * Применяет смещение силуэта руин к базовой (константной) высоте
     * стены зоны, ограничивая результат безопасными пределами:
     * стена никогда не проваливается настолько, чтобы лабиринт
     * перестал быть закрытым сверху, и не взлетает нелепо высоко.
     */
    private int applyRuinOffset(int baseHeight, int x, int z) {
        int offset = ruinHeightOffset(x, z);
        int lower = Math.max(14, baseHeight - 14);
        int upper = baseHeight + 6;
        int result = baseHeight + offset;
        if (result < lower) result = lower;
        if (result > upper) result = upper;
        return result;
    }

    private boolean isCarveSafe(int dH, int thickness) {
        return dH < MAX_CARVE_DEPTH && dH <= thickness - 1 - MAX_CARVE_DEPTH;
    }

    private double crackVein(int x, int y, int z) {
        double jx = featureNoise.noise(x * 0.11, y * 0.11, z * 0.11) * 0.6;
        double jy = featureNoise.noise(x * 0.11 + 137, y * 0.11 + 137, z * 0.11 + 137) * 0.6;
        double jz = featureNoise.noise(x * 0.11 + 291, y * 0.11 + 291, z * 0.11 + 291) * 0.6;
        return Math.abs(featureNoise.noise(x * 0.045 + jx, y * 0.020 + jy, z * 0.045 + jz));
    }

    private double crackZone(int x, int y, int z) {
        return terrainNoise.noise(x * 0.012, y * 0.012, z * 0.012);
    }

    private double crackSegmentMask(int x, int y, int z) {
        return terrainNoise.noise(x * 0.03 + 500, y * 0.045 + 500, z * 0.03 + 500);
    }

    private double hash01(int x, int y, int z) {
        long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L) ^ seed;
        h ^= h >>> 32; h *= 0x85EBCA77C2B2AE63L;
        h ^= h >>> 27; h *= 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        return (h & 0xFFFF) / 65535.0;
    }

    // =====================================================================
    // ★ УМНЫЕ ПРУТЬЯ: проверяют соседей и соединяются/удаляются ★
    // =====================================================================

    /** Проверка: является ли блок твёрдым (стена/кирпич/другие прутья, но НЕ воздух) */
    private boolean isSupportBlock(int x, int y, int z) {
        int[] geo = wallGeometry(x, z);
        int dH = geo[0];
        int wallHeight = geo[2];
        int dV = (FLOOR_Y + wallHeight) - y;

        // За пределами стены = опора
        if (y < FLOOR_Y || dV < 0) return true;

        // Проверяем: это блок стены или воздух?
        double zone = crackZone(x, y, z);
        double base = (0.06 + (zone + 1.0) * 0.025) * erosionIntensity(x, z);
        double vein = crackVein(x, y, z);
        boolean visible = crackSegmentMask(x, y, z) > -0.1;

        // Верхушка = всегда твёрдая
        if (dV < dH) return true;

        // Если это воздух трещины - не опора
        if (visible && dH < MAX_CARVE_DEPTH) {
            double t = base * (1.0 - 0.38 * dH);
            if (vein < t && isCarveSafe(dH, geo[1])) {
                return false; // воздух
            }
        }

        return true; // стена или заполненная трещина
    }

    /** Умные прутья: соединяются с опорой, если висят - воздух */
    private BlockState smartIronBars(int x, int y, int z) {
        boolean up    = isSupportBlock(x, y + 1, z);
        boolean down  = isSupportBlock(x, y - 1, z);
        boolean north = isSupportBlock(x, y, z - 1);
        boolean south = isSupportBlock(x, y, z + 1);
        boolean west  = isSupportBlock(x - 1, y, z);
        boolean east  = isSupportBlock(x + 1, y, z);

        // Если со всех 6 сторон пусто - заменяем на воздух
        if (!(up || down || north || south || west || east)) {
            return Blocks.AIR.defaultBlockState();
        }

        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        if (north) bars = bars.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, true);
        if (south) bars = bars.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, true);
        if (west)  bars = bars.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, true);
        if (east)  bars = bars.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST, true);
        return bars;
    }

    // =====================================================================
    // ★ ДНО ТРЕЩИНЫ (ОРИГИНАЛ v3 + УМНЫЕ ПРУТЬЯ) ★
    // =====================================================================
    private BlockState crackBottomBlock(int x, int y, int z, int dH) {
        double r = hash01(x, y, z);
        if (dH == 0) {
            // Поверхность: отверстие + изредка арматура/кирпичи
            if (r > 0.80) return smartIronBars(x, y, z);        // умные прутья
            if (r > 0.62) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        }
        // dH == 1: глубже - больше прутьев (рельеф)
        if (r > 0.55) return smartIronBars(x, y, z);
        return Blocks.AIR.defaultBlockState();
    }

    private BlockState filledCrackBlock(int x, int y, int z) {
        double r = hash01(x, y, z);
        if (r < 0.6) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        return Blocks.COBBLESTONE.defaultBlockState();
    }

    private BlockState weatheredSurface(int x, int y, int z, double vein, double base) {
        double r = hash01(x, y, z);
        if (vein < base * 2.0 && r < 0.55) {
            return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        }
        if (r < 0.12) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (r < 0.22) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        if (r < 0.28) return Blocks.COBBLESTONE.defaultBlockState();
        return getWallBlock(x, y, z);
    }

    // =====================================================================
    // ★ ГЛАВНЫЙ МЕТОД (ОРИГИНАЛ v3 + УМНЫЕ ПРУТЬЯ) ★
    // =====================================================================
    private BlockState getDecayedWallBlock(int x, int y, int z, int depthFromSurface) {
        // ★ ЛИСТВЕННАЯ ШАПКА НА КРОМКЕ (верх стены зарастает) ★
        // Раньше здесь тоже был getTopY() — он завышал высоту для стен основного
        // лабиринта, из-за чего условие "y примерно равен высоте стены" почти
        // никогда не выполнялось и кустики просто не рождались. wallTopAt() даёт
        // реальную высоту конкретной стены.
        BlockState cap = tryGenerateVineCap(x, y, z, wallTopAt(x, z));
        if (cap != null) return cap;

        // ... остальной код без изменений
        // ★ АБСОЛЮТНАЯ ЗАЩИТА ФУНДАМЕНТА ★
        if (y < FLOOR_Y + 5) {
            return getWallBlock(x, y, z);
        }
        if (depthFromSurface > 2) {
            return getWallBlock(x, y, z);
        }

        int[] geo = wallGeometry(x, z);
        int dH = geo[0];
        int thickness = geo[1];
        int wallHeight = geo[2];
        int dV = (FLOOR_Y + wallHeight) - y;
        if (dV < 0) return getWallBlock(x, y, z);

        double zone = crackZone(x, y, z);
        double base = (0.06 + (zone + 1.0) * 0.025) * erosionIntensity(x, z);
        double vein = crackVein(x, y, z);
        boolean visible = crackSegmentMask(x, y, z) > -0.1;

        // Верхушка стены — только выветривание
        boolean isTop = dV < dH;
        if (isTop) {
            return weatheredSurface(x, y, z, vein, base);
        }

        if (visible) {
            double t = base * (1.0 - 0.38 * dH);
            if (vein < t) {
                if (dH < MAX_CARVE_DEPTH) {
                    return isCarveSafe(dH, thickness)
                            ? crackBottomBlock(x, y, z, dH)
                            : filledCrackBlock(x, y, z);
                }
                if (dH == MAX_CARVE_DEPTH) {
                    // Трещина "продолжается" вглубь: умные прутья + cracked
                    double r = hash01(x, y, z);
                    if (r < 0.3) return smartIronBars(x, y, z);
                    return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                }
            }
        }

        // Сколотые края вокруг трещины
        if (dH == 0 && vein < base * 2.0) {
            double r = hash01(x, y, z);
            if (r < 0.45) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
            if (r < 0.60) return Blocks.COBBLESTONE.defaultBlockState();
        }

        return weatheredSurface(x, y, z, vein, base);
    }

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
     * ★ КУСТЫ В ЛАБИРИНТЕ ★
     * Органичные кусты у стен: эллипсоид с шумовой "пушистостью",
     * разные размеры (маленькие/средние/большие), азалия на верхушке.
     */
    private BlockState tryGenerateBush(int x, int y, int z) {
        int height = y - FLOOR_Y;
        if (height < 1 || height > 4) return null;

        // 1. Ближайшая стена (1-2 блока)
        int depth = 3;
        int sideCoord = 0;
        int wallX = x;
        int wallZ = z;
        for (int d = 1; d <= 2; d++) {
            if (isLogicalWall(x + d, z)) { depth = d; sideCoord = z; wallX = x + d; wallZ = z; break; }
            if (isLogicalWall(x - d, z)) { depth = d; sideCoord = z; wallX = x - d; wallZ = z; break; }
            if (isLogicalWall(x, z + d)) { depth = d; sideCoord = x; wallX = x; wallZ = z + d; break; }
            if (isLogicalWall(x, z - d)) { depth = d; sideCoord = x; wallX = x; wallZ = z - d; break; }
        }
        if (depth > 2) return null;
        if (movingWallCells.contains(hash(wallX, wallZ))) return null;

        // 2. Центры кустов каждые 5 блоков вдоль стены (~70% мест)
        int centerSide = (int) (Math.round(sideCoord / 5.0) * 5.0);
        double centerNoise = terrainNoise.noise(centerSide * 0.3, centerSide * 0.1, 0);
        if (centerNoise < -0.3) return null;

        int offsetSide = sideCoord - centerSide;
        if (Math.abs(offsetSide) > 4) return null;

        // 3. ★ РАЗНЫЙ РАЗМЕР кустов (0.8..1.3) — не все одинаковые ★
        double sizeNoise = featureNoise.noise(centerSide * 0.7, 0, 77);
        double size = 0.8 + (sizeNoise + 1.0) * 0.25;

        // 4. "Пушистый" эллипсоид
        double sideNorm = (offsetSide * offsetSide) / (16.0 * size);
        double heightNorm = (height * height) / (16.0 * size);
        double depthNorm = (depth * depth) / (9.0 * size);
        double dist = Math.sqrt(sideNorm + heightNorm + depthNorm);
        double perturb = featureNoise.noise(x * 0.5, y * 0.5, z * 0.5) * 0.25;

        if (dist + perturb < 0.85) {
            long hash = (x * 123L) ^ (y * 456L) ^ (z * 789L) ^ seed;
            // Верхушка иногда цветущая азалия
            if (height == 4 && (hash & 0xFF) < 38) {
                return Blocks.FLOWERING_AZALEA.defaultBlockState();
            }
            return getLeafBlock();
        }
        return null;
    }
    // ★ ФИКСИРОВАННАЯ ВЫСОТА ЛИАН: сколько блоков свисают от верха стены ★
    private static final int VINE_HANG = 12;    // ← меняй на любое число (8, 15, 20...)
    private static final int VINE_SPREAD = 2;   // ±2 блока разброса (0 = идеально ровно)

    /** ★ ТОЧНЫЙ верх стены для колонки (getTopY врёт для коридоров лабиринта) ★ */
    private int wallTopAt(int wx, int wz) {
        int d = Math.max(Math.abs(wx), Math.abs(wz));
        if (d <= GLADE_WALL_END) return FLOOR_Y + applyRuinOffset(GLADE_WALL_HEIGHT, wx, wz);
        if (d < MAIN_MAZE_END) return FLOOR_Y + applyRuinOffset(MAZE_HEIGHT, wx, wz);
        if (d <= SEPARATOR_WALL_END) return FLOOR_Y + applyRuinOffset(SEPARATOR_WALL_HEIGHT, wx, wz);
        if (d <= SECTORS_END) {
            boolean internal = (Math.abs(wx) <= 2 || Math.abs(wz) <= 2
                    || Math.abs(wx - wz) <= 2 || Math.abs(wx + wz) <= 2);
            return FLOOR_Y + applyRuinOffset((d >= SECTORS_END || internal) ? SEPARATOR_WALL_HEIGHT : MAZE_HEIGHT, wx, wz);
        }
        return FLOOR_Y + applyRuinOffset(OUTER_WALL_HEIGHT, wx, wz);
    }

    /**
     * ★ ЗЕЛЕНЬ НА СТЕНАХ: ТОЛЬКО ТРОПИЧЕСКАЯ ЛИСТВА ★
     * Никаких блоков VINE — шапки и свисающие пряди целиком из листвы.
     */
    private BlockState tryGenerateWallVines(int x, int y, int z) {
        // Не трогаем саму колонку стены
        if (isLogicalWall(x, z)) return null;

        int[] wall = findNearestWall(x, z);
        // Вплотную к стене (depth=1) или через блок воздуха (depth=2) —
        // второй случай даёт отдельным пышным гроздям немного объёма и
        // позволяет им выступать наружу, а не лежать плоской наклейкой.
        if (wall == null || wall[0] > 2) return null;
        int depth = wall[0];
        int wallTop = wall[1];
        int dir = wall[2];

        int wallVineWx = x + (dir == 0 ? depth : dir == 1 ? -depth : 0);
        int wallVineWz = z + (dir == 2 ? depth : dir == 3 ? -depth : 0);
        if (movingWallCells.contains(hash(wallVineWx, wallVineWz))) return null;

        int hang = wallTop - y;
        if (hang < 1 || hang > 45) return null;          // только ниже кромки

        // Крупная региональная плотность зарослей — те же густые/редкие/
        // почти голые участки, что и у остальных лиан, а не отдельная
        // логика только для этой функции.
        if (!isLianaRope(x, z)) return null;

        int t = (dir == 0 || dir == 1) ? z : x;
        if (!organicVineShape(x, y, z, dir, t, hang, depth)) return null;

        // ★ ВСЕГДА ЛИСТВА — блоки VINE больше не генерируются ★
        return getJungleLeafBlock();
    }

    // =====================================================================
    // ★ ОБЪЁМНАЯ СИСТЕМА ЛИАН (v2) ★
    // Раньше форма пряди была одной и той же функцией на фиксированной
    // сетке якорей (каждые 8 блоков) — это и давало эффект сплошной
    // прямоугольной "плиты" из листвы. Теперь:
    //   - якоря групп расставлены НЕРАВНОМЕРНО (сетка ячеек с джиттером,
    //     а не жёсткий шаг);
    //   - часть ячеек вообще пустая — между группами есть настоящие
    //     разрывы, а не сплошной ковёр;
    //   - у каждой группы случайно (по seed мира) выбирается один из
    //     нескольких алгоритмов формы — тонкая извивающаяся плеть,
    //     пышная объёмная гроздь с боковой веткой, несколько редких
    //     тонких нитей с разрывами, асимметричная смещённая занавесь;
    //   - у каждой группы свой случайный размер (короче/длиннее,
    //     тоньше/толще) — маленькие, средние и крупные скопления;
    //   - часть групп может слегка выступать от стены наружу (depth=2),
    //     создавая настоящий объём, а не плоскую грань.
    // Форма стен/коридоров этой системой не затрагивается — она решает
    // только, ставить ли блок листвы в уже "воздушной" клетке рядом
    // со стеной.
    // =====================================================================
    private static final int VINE_CELL_LEN = 3;

    /** Детерминированный (от seed мира) хэш для конкретной ячейки вдоль стены. */
    private long vineGroupHash(int cellIdx, int dir, long salt) {
        long h = ((long) cellIdx * 0x9E3779B97F4A7C15L) ^ ((long) dir * 0xC2B2AE3D27D4EB4FL)
                ^ (seed * 0x2545F4914F6CDD1DL) ^ salt;
        h ^= (h >>> 33);
        h *= 0xFF51AFD7ED558CCDL;
        h ^= (h >>> 33);
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= (h >>> 33);
        return h;
    }

    /** Достаёт независимое псевдослучайное число [0,1) из хэша (slice 0..3). */
    private double vineRnd(long h, int slice) {
        long shifted = h >>> (slice * 16);
        return (shifted & 0xFFFFL) / 65535.0;
    }

    private boolean organicVineShape(int x, int y, int z, int dir, int t, int hang, int depth) {
        if (depth < 1 || depth > 2) return false;

        int baseCell = Math.floorDiv(t, VINE_CELL_LEN);
        for (int co = -1; co <= 1; co++) {
            int cellIdx = baseCell + co;
            long gh = vineGroupHash(cellIdx, dir, 0x51A5L);
            if (vineRnd(gh, 0) < 0.24) continue;

            // Якорь "гуляет" внутри ячейки — расстояния между соседними
            // группами неровные, а не строго по сетке.
            int jitter = (int) Math.round((vineRnd(gh, 1) - 0.5) * (VINE_CELL_LEN - 2));
            int anchor = cellIdx * VINE_CELL_LEN + VINE_CELL_LEN / 2 + jitter;
            int dt = t - anchor;
            if (Math.abs(dt) > 5) continue; // группа физически не дотянется настолько далеко

            int style = (int) (vineRnd(gh, 2) * 4.0);
            if (style > 3) style = 3;

            double sizeRoll = vineRnd(gh, 3);
            // ★ Крупные группы стали реже и меньше (было до 1.4x) — иначе
            // самые пышные скопления превращались в заметный прямоугольный
            // выступ вместо небольшой неровной кроны.
            double sizeScale = sizeRoll < 0.5 ? 0.65 : (sizeRoll < 0.9 ? 0.95 : 1.15);

            if (vineGroupMember(x, y, z, dir, dt, hang, depth, style, sizeScale, gh)) {
                if (depth == 2) {
                    // ★ Внешний (выступающий) слой разрежаем шумом ★
                    // Без этого depth=2 давал сплошную вторую "стену" из
                    // листвы поверх первой. Теперь наружу торчат только
                    // отдельные плотные островки, а не целый слой.
                    double punch = terrainNoise.noise(x * 1.3 + 91.0, y * 1.3, z * 1.3 + 91.0);
                    if (punch < 0.15) return false;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Проверка принадлежности конкретной точки одной группе лиан по
     * выбранному для неё "стилю" — своя форма, длина и толщина у
     * каждого стиля, плюс мелкая рябь по краю, чтобы контур никогда не
     * был ровной линией.
     */
    private boolean vineGroupMember(int x, int y, int z, int dir, int dt, int hang, int depth,
                                    int style, double sizeScale, long gh) {
        double ripple = terrainNoise.noise(x * 0.8, y * 0.8, z * 0.8) * 0.4;

        switch (style) {
            case 0: {
                // Тонкая короткая плеть с крючком-завитком на конце —
                // почти прямая у корня, закручивается ближе к кончику,
                // как на образце. Толщина в один блок, без раздутых пятен.
                int len = (int) Math.round((8 + vineRnd(gh, 6) * 7) * sizeScale);
                if (len <= 0 || hang > len) return false;
                if (depth > 1) return false;
                double progress = (double) hang / len;
                double swayPhase = vineRnd(gh, 5) * 6.283;
                double turns = 0.6 + vineRnd(gh, 2) * 0.6;
                double amp = 1.6 + vineRnd(gh, 3) * 1.0;
                double curl = Math.pow(progress, 1.4);
                double sway = Math.sin(progress * turns * 6.283 + swayPhase) * amp * curl;
                double radius = 1 + (1.0 - progress) * 1;
                double d = Math.abs(dt - sway) - ripple * 0.5;
                return d <= radius;
            }
            case 1: {
                // Пышная объёмная гроздь: толще у стены, с отдельной боковой
                // веткой — и именно она может слегка выступать наружу.
                int len = (int) Math.round((4 + vineRnd(gh, 4) * 6) * sizeScale);
                if (len <= 0 || hang > len) return false;
                double taper = 1.0 - (double) hang / len;
                double bulge = featureNoise.noise(dt * 0.9 + dir * 13 + (gh & 0xFF),
                        hang * 0.4, (dir + 1) * 37.0) * 0.4;
                // ★ Радиус и штраф за выступ наружу уменьшены ★
                // Раньше самая пышная гроздь (до 3.5 блока в поперечнике)
                // могла легко "дотянуться" до depth=2, из-за чего заросли
                // выглядели одной большой прямоугольной массой, а не кроной,
                // прижатой к стене.
                double radius = (0.65 + taper * 0.85 + bulge) * sizeScale;
                double dLat = Math.abs(dt) - ripple;
                double outward = (depth - 1) * 1.8;
                double core = Math.sqrt(dLat * dLat + outward * outward);
                if (core <= radius) return true;
                if (depth > 1) return false;

                // Боковая ветка чуть ниже центра грозди — смещена в сторону.
                double branchHang = len * (0.35 + vineRnd(gh, 6) * 0.3);
                double branchDt = dt - ((vineRnd(gh, 7) - 0.5) * 5.0);
                double branchRadius = 0.55 * sizeScale;
                double bDy = (hang - branchHang) * 0.6;
                double bDist = Math.sqrt(branchDt * branchDt + bDy * bDy);
                return bDist <= branchRadius;
            }
            case 2: {
                // Несколько редких тонких нитей с разрывами по высоте —
                // вместо одной сплошной пряди.
                if (depth > 1) return false;
                int strands = 2 + (int) (vineRnd(gh, 4) * 2.0);
                for (int s = 0; s < strands; s++) {
                    long sh = vineGroupHash((int) (gh & 0xFFFF), dir, 0x9000L + s);
                    double strandDt = (vineRnd(sh, 0) - 0.5) * 5.0;
                    int len = (int) Math.round((6 + vineRnd(sh, 1) * 12) * sizeScale);
                    if (hang > len) continue;
                    double gap = featureNoise.noise((x + s * 11) * 0.45, y * 0.55, (z + s * 7) * 0.45);
                    if (gap < -0.2) continue; // разрыв в нити
                    double radius = 0.42 * sizeScale;
                    if (Math.abs(dt - strandDt) - ripple <= radius) return true;
                }
                return false;
            }
            default: {
                // Асимметричная занавесь, смещённая в сторону от якоря —
                // никакой симметрии относительно центра группы.
                int len = (int) Math.round((7 + vineRnd(gh, 4) * 9) * sizeScale);
                if (len <= 0 || hang > len) return false;
                double taper = 1.0 - (double) hang / len;
                double drift = (vineRnd(gh, 5) - 0.5) * 3.2;
                double curve = drift * (1.0 - taper) + Math.sin(hang * 0.22) * 0.4;
                double radius = (0.6 + taper * 0.75) * sizeScale;
                double d = Math.abs(dt - curve) - ripple;
                if (d > radius) return false;
                if (depth == 1) return true;
                return radius > 1.05 && Math.abs(dt - curve) < radius * 0.4;
            }
        }
    }

    private BlockState getLeafBlock() {
        try {
            return Blocks.OAK_LEAVES.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.PERSISTENT, true
            );
        } catch (Exception e) {
            return Blocks.OAK_LEAVES.defaultBlockState();
        }
    }
    private BlockState generateNaturalTerrain(int x, int y, int z, int dist, double riverDist) {
        if (dist <= GLADE_RADIUS) {
            double noise = terrainNoise.noise(x * 0.04, 0, z * 0.04);
            int terrainHeight = FLOOR_Y + (int)(noise * 5);
            double passageBlend = getPassageBlendFactor(x, z);
            if (passageBlend > 0.0) {
                // Плавная интерполяция: чем ближе к проходу, тем ближе к FLOOR_Y + 1
                double smoothBlend = passageBlend * passageBlend * (3.0 - 2.0 * passageBlend);
                int targetHeight = FLOOR_Y;
                terrainHeight = (int)(terrainHeight * (1.0 - smoothBlend) + targetHeight * smoothBlend);
            }

            if (passageBlend > 0.0) {
                double smoothBlend = passageBlend * passageBlend * (3.0 - 2.0 * passageBlend);
                int targetHeight = FLOOR_Y;
                terrainHeight = (int)(terrainHeight * (1.0 - smoothBlend) + targetHeight * smoothBlend);
            }

            StructureBlendResult structureBlend = getStructureBlendResult(x, z);

            if (structureBlend.factor > 0.0) {
                double smoothBlend = structureBlend.factor * structureBlend.factor * (3.0 - 2.0 * structureBlend.factor);
                terrainHeight = (int)(terrainHeight * (1.0 - smoothBlend) + structureBlend.targetY * smoothBlend);
            }

            if (structureBlend.core) {
                if (y > terrainHeight) {
                    return Blocks.AIR.defaultBlockState();
                }

                if (y == terrainHeight) {
                    return GLADE_TOP;
                }

                if (y > terrainHeight - 4) {
                    return GLADE_UNDER;
                }

                return generateUnderground(x, y, z);
            }

            // ★ РЕКА через расстояние до кривой Безье ★
            if (riverDist >= 0 && structureBlend.factor < 0.35) {
                double widthNoise = featureNoise.noise(x * 0.05, 0, z * 0.05) * 1.5;
                double waterHalfWidth = 8.5 + widthNoise;
                waterHalfWidth = Math.max(7.0, waterHalfWidth);

                double bankHalfWidth = 3.0;
                double totalHalfWidth = waterHalfWidth + bankHalfWidth;

                if (riverDist < totalHalfWidth) {
                    double depthNoise = featureNoise.noise(x * 0.05, 0, z * 0.05);
                    int maxDepth = 4 + (int)(depthNoise * 1.5);
                    maxDepth = Math.max(3, Math.min(5, maxDepth));
                    int waterLevel = FLOOR_Y - 1;

                    if (riverDist < waterHalfWidth) {
                        // ===== В ВОДЕ =====
                        double t = 1.0 - (riverDist / waterHalfWidth);
                        double smoothT = t * t * t * (t * (t * 6.0 - 15.0) + 10.0);

                        double bottomNoise = terrainNoise.noise(x * 0.12, 0, z * 0.12) * 1.5;
                        double bottomNoise2 = featureNoise.noise(x * 0.18, 0, z * 0.18) * 0.5;
                        int riverBottomY = waterLevel - (int)(smoothT * maxDepth) + (int)(bottomNoise + bottomNoise2);

                        if (riverBottomY >= waterLevel) {
                            riverBottomY = waterLevel - 1;
                        }

                        if (y == riverBottomY || y == riverBottomY - 1) {
                            return getRiverBedBlock(x, y, z);
                        }
                        if (y < riverBottomY - 1) {
                            return generateUnderground(x, y, z);
                        }

                        // ★ ПОДВОДНАЯ ТРАВА: качественный хеш ★
                        if (y == riverBottomY + 1 && y < waterLevel) {
                            long mixed = ((long) x * 73856093L) ^ ((long) z * 83492791L) ^ this.seed;
                            mixed ^= (mixed >>> 32);
                            mixed *= 0x85EBCA77C2B2AE63L;
                            mixed ^= (mixed >>> 27);
                            mixed *= 0xC2B2AE3D27D4EB4FL;
                            mixed ^= (mixed >>> 31);
                            double seaChance = (mixed & 0xFF) / 255.0;
                            if (seaChance < 0.35) {
                                return Blocks.SEAGRASS.defaultBlockState();
                            }
                            return Blocks.WATER.defaultBlockState();
                        }

                        if (y <= waterLevel) {
                            return Blocks.WATER.defaultBlockState();
                        }

                        // ★ КУВШИНКИ: качественный хеш ★
                        if (y == waterLevel + 1) {
                            long mixed = ((long) x * 73856093L) ^ ((long) z * 83492791L) ^ this.seed;
                            mixed ^= (mixed >>> 32);
                            mixed *= 0x85EBCA77C2B2AE63L;
                            mixed ^= (mixed >>> 27);
                            mixed *= 0xC2B2AE3D27D4EB4FL;
                            mixed ^= (mixed >>> 31);
                            double lilyChance = (mixed & 0xFF) / 255.0;
                            if (lilyChance < 0.06 && riverDist < waterHalfWidth * 0.7) {
                                return Blocks.LILY_PAD.defaultBlockState();
                            }
                            return Blocks.AIR.defaultBlockState();
                        }
                        return Blocks.AIR.defaultBlockState();

                    } else {
                        // ===== БЕРЕГ =====
                        double bankT = (riverDist - waterHalfWidth) / bankHalfWidth;
                        bankT = Math.max(0.0, Math.min(1.0, bankT));
                        double bankSmoothT = bankT * bankT * bankT * (bankT * (bankT * 6.0 - 15.0) + 10.0);

                        double noiseFactor = bankSmoothT * (1.0 - bankSmoothT) * 4.0;
                        double bankNoise = featureNoise.noise(x * 0.06, 0, z * 0.06) * 1.5 * noiseFactor;
                        double bankNoise2 = terrainNoise.noise(x * 0.10, 0, z * 0.10) * 1.0 * noiseFactor;
                        double bankNoise3 = featureNoise.noise(x * 0.18, 0, z * 0.18) * 0.5 * noiseFactor;

                        int bankHeight = waterLevel + (int)(bankSmoothT * (terrainHeight - waterLevel)
                                + bankNoise + bankNoise2 + bankNoise3);

                        if (bankHeight < waterLevel) {
                            bankHeight = waterLevel;
                        }

                        // ★ РАСТИТЕЛЬНОСТЬ НА БЕРЕГУ: как в остальном глейде ★
                        if (y > bankHeight) {
                            if (y == bankHeight + 1 || y == bankHeight + 2) {
                                long mixed = ((long) x * 73856093L) ^ ((long) z * 83492791L) ^ this.seed;
                                mixed ^= (mixed >>> 32);
                                mixed *= 0x85EBCA77C2B2AE63L;
                                mixed ^= (mixed >>> 27);
                                mixed *= 0xC2B2AE3D27D4EB4FL;
                                mixed ^= (mixed >>> 31);

                                double chance = (mixed & 0xFF) / 255.0;
                                int typeByte = (int)((mixed >>> 8) & 0xFF);

                                if (chance < 0.30) {
                                    // Чистый дёрн
                                    return Blocks.AIR.defaultBlockState();
                                } else if (chance < 0.70) {
                                    // Обычная трава
                                    if (y == bankHeight + 1) {
                                        return Blocks.GRASS.defaultBlockState();
                                    }
                                    return Blocks.AIR.defaultBlockState();
                                } else if (chance < 0.85) {
                                    // Высокая трава
                                    if (y == bankHeight + 1) {
                                        return Blocks.TALL_GRASS.defaultBlockState()
                                                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
                                    } else if (y == bankHeight + 2) {
                                        return Blocks.TALL_GRASS.defaultBlockState()
                                                .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
                                    }
                                    return Blocks.AIR.defaultBlockState();
                                } else {
                                    // Цветы (12 видов)
                                    if (y == bankHeight + 1) {
                                        int flowerType = typeByte % 12;
                                        return getFlowerByType(flowerType);
                                    }
                                    return Blocks.AIR.defaultBlockState();
                                }
                            }
                            return Blocks.AIR.defaultBlockState();
                        }

                        if (y == bankHeight) {
                            double shoreNoise = terrainNoise.noise(x * 0.12, 0, z * 0.12);
                            double shoreNoise2 = featureNoise.noise(x * 0.18, 0, z * 0.18);
                            if (shoreNoise < -0.3 || shoreNoise2 > 0.5) {
                                return Blocks.SAND.defaultBlockState();
                            } else if (shoreNoise2 < -0.4) {
                                return Blocks.GRAVEL.defaultBlockState();
                            }
                            return GLADE_TOP;
                        }
                        if (y > bankHeight - 4) {
                            double underNoise = featureNoise.noise(x * 0.1, 0, z * 0.1);
                            if (underNoise < -0.3) return Blocks.SAND.defaultBlockState();
                            if (underNoise < 0.0) return Blocks.CLAY.defaultBlockState();
                            return GLADE_UNDER;
                        }
                        return generateUnderground(x, y, z);
                    }
                }
            }
            if (structureBlend.factor <= 0.0 && dist >= 30 && dist <= 55) {
                // Используем два слоя шума для создания плавных, органичных возвышенностей
                double hillNoise1 = featureNoise.noise(x * 0.04, 0, z * 0.04);
                double hillNoise2 = terrainNoise.noise(x * 0.08, 0, z * 0.08);

                // Комбинируем шумы
                double combinedNoise = (hillNoise1 + hillNoise2) * 0.5;

                // Если значение шума достаточно высокое, формируем холм
                if (combinedNoise > 0.4) {
                    // Масштабируем высоту: от 0 до 6 блоков
                    double heightFactor = (combinedNoise - 0.4) / 0.6; // нормализуем значение от 0 до 1
                    int addedHeight = (int)(heightFactor * 6.0);

                    // ★ ЖЕСТКИЙ ЛИМИТ: холм не может быть выше 6 блоков и не может уходить в минус ★
                    addedHeight = Math.max(0, Math.min(addedHeight, 6));
                    terrainHeight += addedHeight;
                }
            }
            if (y > terrainHeight) {
                if (y == terrainHeight + 1 || y == terrainHeight + 2) {
                    // ★ КАЧЕСТВЕННЫЙ ХЕШ для равномерного распределения ★
                    long mixed = ((long) x * 73856093L) ^ ((long) z * 83492791L) ^ this.seed;
                    mixed ^= (mixed >>> 32);
                    mixed *= 0x85EBCA77C2B2AE63L;
                    mixed ^= (mixed >>> 27);
                    mixed *= 0xC2B2AE3D27D4EB4FL;
                    mixed ^= (mixed >>> 31);

                    double chance = (mixed & 0xFF) / 255.0;
                    int typeByte = (int)((mixed >>> 8) & 0xFF);

                    if (chance < 0.30) {
                        // ★ 30% чистый дёрн (ничего) ★
                        return Blocks.AIR.defaultBlockState();
                    } else if (chance < 0.70) {
                        // ★ 40% обычная трава ★
                        if (y == terrainHeight + 1) {
                            return Blocks.GRASS.defaultBlockState();
                        }
                        return Blocks.AIR.defaultBlockState();
                    } else if (chance < 0.85) {
                        // ★ 15% высокая трава (двухблочная) ★
                        if (y == terrainHeight + 1) {
                            return Blocks.TALL_GRASS.defaultBlockState()
                                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER);
                        } else if (y == terrainHeight + 2) {
                            return Blocks.TALL_GRASS.defaultBlockState()
                                    .setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER);
                        }
                        return Blocks.AIR.defaultBlockState();
                    } else {
                        // ★ 15% цветы (12 видов) ★
                        if (y == terrainHeight + 1) {
                            int flowerType = typeByte % 12;
                            return getFlowerByType(flowerType);
                        }
                        return Blocks.AIR.defaultBlockState();
                    }
                }
                return Blocks.AIR.defaultBlockState();
            }
            if (y == terrainHeight) return GLADE_TOP;
            if (y > terrainHeight - 4) return GLADE_UNDER;

            return generateUnderground(x, y, z);

        } else {
            double duneNoise = featureNoise.noise(x * 0.015, 0, z * 0.015);
            int desertHeight = FLOOR_Y + (int)(duneNoise * 12);

            if (y > desertHeight) return Blocks.AIR.defaultBlockState();
            if (y == desertHeight) return Blocks.SAND.defaultBlockState();
            if (y > desertHeight - 5) return Blocks.SAND.defaultBlockState();
            if (y > desertHeight - 20) return Blocks.SANDSTONE.defaultBlockState();

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
        if (y == FLOOR_Y) return randomFloorBlock(x, z);
        if (y > FLOOR_Y && y <= FLOOR_Y + MAZE_HEIGHT) {
            if (passages.contains(hash) || passageZones.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                BlockState vine = tryGenerateVine(x, y, z, FLOOR_Y + MAZE_HEIGHT);
                if (vine != null) return vine;
                return Blocks.AIR.defaultBlockState();
            }



            if (y > FLOOR_Y + MAZE_HEIGHT) {
                BlockState vine = tryGenerateWallVines(x, y, z);   // ★ лианы вдоль высоких стен
                if (vine != null) return vine;
                return Blocks.AIR.defaultBlockState();
            }


            if (mazeCorridors.contains(hash)) {
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                BlockState vine = tryGenerateWallVines(x, y, z);   // ★
                if (vine != null) return vine;
                return Blocks.AIR.defaultBlockState();
            }
            if (passages.contains(hash) || passageZones.contains(hash)) {
                BlockState spanning = tryGenerateSpanningVine(x, y, z);
                if (spanning != null) return spanning;
                BlockState bush = tryGenerateBush(x, y, z);
                if (bush != null) return bush;
                BlockState vine = tryGenerateVine(x, y, z, FLOOR_Y + MAZE_HEIGHT);
                if (vine != null) return vine;
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

    private BlockState randomFloorBlock(int x, int z) {
        long mixed = ((long) x * 73856093L) ^ ((long) z * 83492791L) ^ this.seed;

        mixed ^= (mixed >>> 32);
        mixed *= 0x85EBCA77C2B2AE63L;
        mixed ^= (mixed >>> 27);
        mixed *= 0xC2B2AE3D27D4EB4FL;
        mixed ^= (mixed >>> 31);

        return ((mixed & 0xFF) < 128) ? FLOOR_ANDESITE : FLOOR_POLISHED_ANDESITE;
    }

    @Override
    public void addDebugScreenInfo(@NotNull List<String> list, @NotNull RandomState random, @NotNull BlockPos pos) {
        ensureGenerated();
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
        if (dist <= GLADE_WALL_END) return FLOOR_Y + applyRuinOffset(GLADE_WALL_HEIGHT, x, z);
        if (dist <= SEPARATOR_WALL_END) return FLOOR_Y + applyRuinOffset(SEPARATOR_WALL_HEIGHT, x, z);
        if (dist <= OUTER_WALL_END) return FLOOR_Y + applyRuinOffset(OUTER_WALL_HEIGHT, x, z);

        boolean isInternalWall = (Math.abs(x) <= 2 || Math.abs(z) <= 2 || Math.abs(x - z) <= 2 || Math.abs(x + z) <= 2);
        if (dist <= SECTORS_END && isInternalWall) return FLOOR_Y + applyRuinOffset(SEPARATOR_WALL_HEIGHT, x, z);

        return FLOOR_Y + applyRuinOffset(MAZE_HEIGHT, x, z);
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

    private int[] findNearestWall(int x, int z) {
        for (int d = 1; d <= 8; d++) { // ★ Увеличено с 4 до 8 для широких лиан
            if (isLogicalWall(x + d, z)) return new int[]{d, wallTopAt(x + d, z), 0};
            if (isLogicalWall(x - d, z)) return new int[]{d, wallTopAt(x - d, z), 1};
            if (isLogicalWall(x, z + d)) return new int[]{d, wallTopAt(x, z + d), 2};
            if (isLogicalWall(x, z - d)) return new int[]{d, wallTopAt(x, z - d), 3};
            // Диагональные проверки
            if (isLogicalWall(x + d, z + d)) return new int[]{d, wallTopAt(x + d, z + d), 0};
            if (isLogicalWall(x - d, z - d)) return new int[]{d, wallTopAt(x - d, z - d), 1};
            if (isLogicalWall(x + d, z - d)) return new int[]{d, wallTopAt(x + d, z - d), 0};
            if (isLogicalWall(x - d, z + d)) return new int[]{d, wallTopAt(x - d, z + d), 1};
        }
        return null;
    }




    // КЭШ ДЛЯ СТОЛБЦОВ (X, Z)
    private static final Map<Long, Boolean> lianaCache = new ConcurrentHashMap<>();

    /**
     * ★ 根据墙壁方向生成侧面贴合的原版藤蔓 ★
     */
    private BlockState getVineBlockFacingWall(int wallDir) {
        BlockState vine = Blocks.VINE.defaultBlockState();

        switch (wallDir) {
            // Стена справа от лианы (+X)
            case 0:
                return vine.setValue(
                        BlockStateProperties.EAST, true
                );

            // Стена слева от лианы (-X)
            case 1:
                return vine.setValue(
                        BlockStateProperties.WEST, true
                );

            // Стена впереди (+Z)
            case 2:
                return vine.setValue(
                        BlockStateProperties.SOUTH, true
                );

            // Стена сзади (-Z)
            case 3:
                return vine.setValue(
                        BlockStateProperties.NORTH, true
                );

            default:
                return vine;
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
        /*
         * Листва используется только как декоративная часть
         * зарослей/верхушек.
         *
         * PERSISTENT = true:
         * листья не будут исчезать из-за отсутствия дерева.
         */

        return Blocks.JUNGLE_LEAVES.defaultBlockState()
                .setValue(
                        BlockStateProperties.PERSISTENT,
                        true
                );
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

    }

    private void ensureGenerated() {
        if (isGenerated) return;

        // Сначала регистрируем базовые структуры и загружаем их размеры.
        StructureGenerator.preloadGladeSizes();

        StructureGenerator.updateDverPosition(this.GLADE_RADIUS);
        initializeBridge();

        // После добавления dver/most снова загружаем размеры,
        // чтобы проверка пересечений видела уже ВСЕ структуры.
        StructureGenerator.preloadGladeSizes();

        initializeGladeStructures();

        synchronized (generationLock) {
            if (isGenerated) return;

            generateMaze();
            generateSectors();
            generatePassages();
            validateAndFixRealPassages();

            isGenerated = true;
        }
    }
    // ★ БАЗОВАЯ ПОЛУШИРИНА ВОДЫ (без учёта сужения у моста) ★
    private double computeBaseWaterHalfWidth(int x, int z) {
        if (featureNoise == null) {
            return 10.0;
        }

        double widthNoise = featureNoise.noise(x * 0.05, 0, z * 0.05) * 1.5;
        double waterHalfWidth = 8.5 + widthNoise;
        return Math.max(6.0, waterHalfWidth);
    }

    private volatile double bridgeCenterX = 0.0;
    private volatile double bridgeCenterZ = 0.0;
    private volatile double bridgeFlowUx = 1.0;
    private volatile double bridgeFlowUz = 0.0;
    private volatile boolean bridgeNarrowingActive = false;
    private volatile double bridgeForcedWaterHalfWidth = 6.0;
    private volatile double bridgeNarrowRadius = 6.0;
    private volatile double bridgeNarrowBlend = 6.0;

    private double applyBridgeNarrowing(int x, int z, double waterHalfWidth) {
        if (!bridgeNarrowingActive) return waterHalfWidth;

        double dx = x - bridgeCenterX;
        double dz = z - bridgeCenterZ;
        double dist = Math.abs(dx * bridgeFlowUx + dz * bridgeFlowUz);

        if (dist >= bridgeNarrowRadius + bridgeNarrowBlend) {
            return waterHalfWidth;
        }

        double narrowed = Math.min(waterHalfWidth, bridgeForcedWaterHalfWidth);

        if (dist <= bridgeNarrowRadius) {
            return narrowed;
        }

        double t = (dist - bridgeNarrowRadius) / bridgeNarrowBlend;
        t = t * t * (3.0 - 2.0 * t);
        return narrowed + (waterHalfWidth - narrowed) * t;
    }

    private void initializeBridge() {
        ensureRiverCurve();
        double[][] points = riverCurvePoints;
        if (points == null || points.length < 20) return;

        Vec3i bridgeSize = StructureGenerator.getBridgeSize();
        int bridgeWidthX = bridgeSize.getX() > 0 ? bridgeSize.getX() : 7;
        int bridgeLengthZ = bridgeSize.getZ() > 0 ? bridgeSize.getZ() : 18;

        double maxWaterHalfWidth = Math.max(3.0,
                bridgeLengthZ / 2.0 - BRIDGE_BANK_HALF_WIDTH - BRIDGE_LAND_MARGIN);

        int windowSize = 15;
        int margin = 5;

        boolean placed = false;

        for (int idx : findNaturalNarrowRiverPoints(points, margin, windowSize)) {
            if (tryPlaceBridgeAt(points, idx, bridgeWidthX, bridgeLengthZ, maxWaterHalfWidth, false)) {
                placed = true;
                break;
            }
        }

        if (!placed) {
            for (int idx : findStraightestSegments(points, windowSize, margin)) {
                if (tryPlaceBridgeAt(points, idx, bridgeWidthX, bridgeLengthZ, maxWaterHalfWidth, true)) {
                    placed = true;
                    break;
                }
            }
        }

        if (!placed) {
            System.out.println("[LabyrinthGenerator] WARNING: no valid bridge placement found with both ends on land");
        }
    }

    private boolean tryPlaceBridgeAt(double[][] points, int midIdx, int bridgeWidthX, int bridgeLengthZ,
                                     double maxWaterHalfWidth, boolean forceNarrowing) {
        int step = 5;
        int idx1 = Math.max(0, midIdx - step);
        int idx2 = Math.min(points.length - 1, midIdx + step);
        if (idx1 == idx2) return false;

        double flowDx = points[idx2][0] - points[idx1][0];
        double flowDz = points[idx2][1] - points[idx1][1];
        double flowLen = Math.sqrt(flowDx * flowDx + flowDz * flowDz);
        if (flowLen < 0.001) return false;

        double flowUx = flowDx / flowLen;
        double flowUz = flowDz / flowLen;

        Rotation baseRot = (Math.abs(flowDx) > Math.abs(flowDz))
                ? Rotation.CLOCKWISE_90
                : Rotation.NONE;

        Rotation bridgeRot = (baseRot == Rotation.NONE)
                ? Rotation.CLOCKWISE_90
                : Rotation.CLOCKWISE_180;

        double cx = points[midIdx][0];
        double cz = points[midIdx][1];

        this.bridgeCenterX = cx;
        this.bridgeCenterZ = cz;
        this.bridgeFlowUx = flowUx;
        this.bridgeFlowUz = flowUz;
        this.bridgeNarrowingActive = forceNarrowing;
        this.bridgeForcedWaterHalfWidth = maxWaterHalfWidth;
        this.bridgeNarrowRadius = bridgeWidthX / 2.0;
        this.bridgeNarrowBlend = Math.max(3.0, bridgeWidthX / 2.0);

        double localWidthCenter = bridgeWidthX / 2.0;
        double[] pointA = rotateXZ(localWidthCenter, 0, bridgeRot);
        double[] pointB = rotateXZ(localWidthCenter, bridgeLengthZ - 1, bridgeRot);

        double crossDx = pointB[0] - pointA[0];
        double crossDz = pointB[1] - pointA[1];
        double crossLen = Math.sqrt(crossDx * crossDx + crossDz * crossDz);
        if (crossLen < 0.001) return false;

        double ucx = crossDx / crossLen;
        double ucz = crossDz / crossLen;

        double halfSpan = (bridgeLengthZ - 1) / 2.0;
        double worldAx = cx - ucx * halfSpan;
        double worldAz = cz - ucz * halfSpan;
        double worldBx = cx + ucx * halfSpan;
        double worldBz = cz + ucz * halfSpan;

        int pointAX = (int) Math.round(worldAx);
        int pointAZ = (int) Math.round(worldAz);
        int pointBX = (int) Math.round(worldBx);
        int pointBZ = (int) Math.round(worldBz);

        if (isInRiverZone(pointAX, pointAZ) || isInRiverZone(pointBX, pointBZ)) {
            return false;
        }

        int originX = (int) Math.round(worldAx - pointA[0]);
        int originZ = (int) Math.round(worldAz - pointA[1]);
        int originY = FLOOR_Y;

        System.out.println("[LabyrinthGenerator] Bridge placed at "
                + (forceNarrowing ? "forced-narrowed" : "natural narrow")
                + " origin=" + originX + "," + originY + "," + originZ
                + " rot=" + bridgeRot
                + " size=" + bridgeWidthX + "x" + bridgeLengthZ
                + " endA=" + pointAX + "," + pointAZ
                + " endB=" + pointBX + "," + pointBZ);

        StructureGenerator.updateBridgePosition(originX, originY, originZ, bridgeRot);
        return true;
    }

    private double[] rotateXZ(double x, double z, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return new double[]{-z, x};
            case CLOCKWISE_180:
                return new double[]{-x, -z};
            case COUNTERCLOCKWISE_90:
                return new double[]{z, -x};
            default:
                return new double[]{x, z};
        }
    }

    private double segmentDeviation(double[][] points, int startIdx, int windowSize) {
        int endIdx = startIdx + windowSize - 1;
        if (startIdx < 0 || endIdx >= points.length) return Double.MAX_VALUE;

        double dirX = points[endIdx][0] - points[startIdx][0];
        double dirZ = points[endIdx][1] - points[startIdx][1];
        double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (dirLen < 0.001) return Double.MAX_VALUE;

        double ndx = dirX / dirLen;
        double ndz = dirZ / dirLen;

        double totalDeviation = 0;
        for (int j = startIdx + 1; j < endIdx; j++) {
            double vx = points[j][0] - points[startIdx][0];
            double vz = points[j][1] - points[startIdx][1];
            double proj = vx * ndx + vz * ndz;
            double closestX = points[startIdx][0] + ndx * proj;
            double closestZ = points[startIdx][1] + ndz * proj;
            double devX = points[j][0] - closestX;
            double devZ = points[j][1] - closestZ;
            totalDeviation += Math.sqrt(devX * devX + devZ * devZ);
        }

        return totalDeviation;
    }

    private List<Integer> findNaturalNarrowRiverPoints(double[][] points, int margin, int windowSize) {
        List<Integer> result = new ArrayList<>();
        int half = windowSize / 2;

        for (int i = margin; i < points.length - margin; i++) {
            int px = (int) Math.round(points[i][0]);
            int pz = (int) Math.round(points[i][1]);

            double waterWidth = computeBaseWaterHalfWidth(px, pz) * 2.0;
            if (waterWidth < BRIDGE_MIN_WIDTH || waterWidth > BRIDGE_MAX_WIDTH) continue;

            double deviation = segmentDeviation(points, i - half, windowSize);
            if (deviation <= BRIDGE_MAX_CURVE_DEVIATION) {
                result.add(i);
            }
        }

        return result;
    }

    private List<Integer> findStraightestSegments(double[][] points, int windowSize, int margin) {
        List<int[]> candidates = new ArrayList<>();

        for (int i = margin; i <= points.length - windowSize - margin; i++) {
            double deviation = segmentDeviation(points, i, windowSize);
            if (deviation < Double.MAX_VALUE) {
                candidates.add(new int[]{i + windowSize / 2, (int) Math.round(deviation * 1000.0)});
            }
        }

        candidates.sort(Comparator.comparingInt(c -> c[1]));

        List<Integer> result = new ArrayList<>();
        for (int[] c : candidates) result.add(c[0]);
        return result;
    }

    private double getRiverTotalHalfWidth(int x, int z) {
        double waterHalfWidth = computeBaseWaterHalfWidth(x, z);
        waterHalfWidth = applyBridgeNarrowing(x, z, waterHalfWidth);

        double bankHalfWidth = 3.0;
        return waterHalfWidth + bankHalfWidth;
    }

    private boolean isInRiverZone(int x, int z) {
        return isInRiverZone(x, z, 0.0);
    }

    private boolean isInRiverZone(int x, int z, double margin) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > GLADE_RADIUS) return false;

        double riverDist = distanceToRiverCurve(x, z);
        double totalHalfWidth = getRiverTotalHalfWidth(x, z);

        return riverDist < totalHalfWidth + margin;
    }
    private BlockState getRiverBedBlock(int x, int y, int z) {
        // Шумовое распределение блоков дна: песок, глина, земля, гравий
        double noise = featureNoise.noise(x * 0.1, y * 0.1, z * 0.1);
        double noise2 = terrainNoise.noise(x * 0.15, y * 0.15, z * 0.15);

        if (noise < -0.35) {
            return Blocks.GRAVEL.defaultBlockState();
        } else if (noise < -0.05) {
            return Blocks.CLAY.defaultBlockState();
        } else if (noise < 0.30) {
            // Песок с примесью гравия
            if (noise2 > 0.4) return Blocks.GRAVEL.defaultBlockState();
            return Blocks.SAND.defaultBlockState();
        } else {
            // Земля с примесью глины
            if (noise2 < -0.4) return Blocks.CLAY.defaultBlockState();
            return Blocks.DIRT.defaultBlockState();
        }
    }
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);

        // ★ РАЗМЕЩЕНИЕ NBT СТРУКТУР ★
        StructureGenerator.placeStructuresInChunk(level, chunk);

        ensureDecorationSeed(level);

        if (!seedInitialized || terrainNoise == null || featureNoise == null) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();

        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        if (minZ >= -30) return;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (z >= -30) continue;

                double distFromCenter = Math.sqrt((double) x * x + (double) z * z);
                if (distFromCenter < 20.0) continue;

                int dist = Math.max(Math.abs(x), Math.abs(z));
                if (dist > GLADE_RADIUS - 5) continue;

                // ★ ПУНКТ 10: не ставим декор в зоне реки ★
                if (isInRiverZone(x, z)) continue;

                // ★ ДЕРЕВЬЯ: сетка 3×3 ★
                int treeGridX = Math.floorDiv(x, 3);
                int treeGridZ = Math.floorDiv(z, 3);

                long treeGridSeed = ((long) treeGridX * 73856093L)
                        ^ ((long) treeGridZ * 19349663L)
                        ^ this.seed;

                Random treeGridRand = new Random(treeGridSeed);

                int chosenTreeX = treeGridX * 3 + 1 + treeGridRand.nextInt(2);
                int chosenTreeZ = treeGridZ * 3 + 1 + treeGridRand.nextInt(2);

                if (x == chosenTreeX && z == chosenTreeZ) {
                    // ★ ПУНКТ 11: шумовая плотность леса ★
                    double densityNoise =
                            terrainNoise.noise(x * 0.02, 0, z * 0.02) * 0.65
                                    + featureNoise.noise(x * 0.055, 0, z * 0.055) * 0.35;

                    // Если шум сильно отрицательный — поляна.
                    if (densityNoise > -0.22) {
                        double treeChance = 0.24 + (densityNoise + 1.0) * 0.16;
                        treeChance = Math.max(0.05, Math.min(0.60, treeChance));

                        if (treeGridRand.nextDouble() < treeChance) {
                            double noise = terrainNoise.noise(x * 0.04, 0, z * 0.04);
                            int surfaceY = FLOOR_Y + (int) (noise * 5) + 1;

                            BlockPos pos = new BlockPos(x, surfaceY, z);
                            BlockPos below = pos.below();

                            BlockState belowState = level.getBlockState(below);

                            if (!belowState.is(Blocks.GRASS_BLOCK) && !belowState.is(Blocks.DIRT)) {
                                continue;
                            }

                            double typeRand = treeGridRand.nextDouble();

                            String featureName;

                            if (typeRand < 0.60) {
                                featureName = "minecraft:fancy_oak";
                            } else if (typeRand < 0.85) {
                                featureName = "minecraft:super_birch_bees";
                            } else {
                                featureName = "minecraft:birch";
                            }

                            RandomSource random = RandomSource.create(treeGridSeed);

                            ConfiguredFeature<?, ?> feature = level.registryAccess()
                                    .registryOrThrow(Registries.CONFIGURED_FEATURE)
                                    .get(ResourceLocation.parse(featureName));

                            if (feature != null) {
                                feature.place(level, this, random, pos);
                            }
                        }
                    }
                }

                // ★ ПОВАЛЕННЫЕ ДЕРЕВЬЯ: сетка 6×6 ★
                int fallenGridX = Math.floorDiv(x, 6);
                int fallenGridZ = Math.floorDiv(z, 6);

                long fallenGridSeed = ((long) fallenGridX * 31337L)
                        ^ ((long) fallenGridZ * 7919L)
                        ^ this.seed;

                Random fallenGridRand = new Random(fallenGridSeed);

                int chosenFallenX = fallenGridX * 6 + fallenGridRand.nextInt(6);
                int chosenFallenZ = fallenGridZ * 6 + fallenGridRand.nextInt(6);

                if (x == chosenFallenX && z == chosenFallenZ && fallenGridRand.nextDouble() < 0.12) {
                    double noise = terrainNoise.noise(x * 0.04, 0, z * 0.04);
                    int surfaceY = FLOOR_Y + (int) (noise * 5) + 1;

                    BlockPos pos = new BlockPos(x, surfaceY, z);
                    BlockPos below = pos.below();

                    BlockState belowState = level.getBlockState(below);

                    if (belowState.is(Blocks.GRASS_BLOCK) || belowState.is(Blocks.DIRT)) {
                        RandomSource fallenRandom = RandomSource.create(fallenGridSeed ^ 0xDEADBEEFL);
                        boolean isOak = fallenGridRand.nextBoolean();
                        generateFallenTree(level, fallenRandom, pos, isOak);
                    }
                }

                // ★ КЛАСТЕРЫ ЦВЕТОВ: сетка 5×5 ★
                int flowerGridX = Math.floorDiv(x, 5);
                int flowerGridZ = Math.floorDiv(z, 5);

                long flowerGridSeed = ((long) flowerGridX * 48271L)
                        ^ ((long) flowerGridZ * 65537L)
                        ^ this.seed;

                Random flowerGridRand = new Random(flowerGridSeed);

                int chosenFlowerX = flowerGridX * 5 + flowerGridRand.nextInt(5);
                int chosenFlowerZ = flowerGridZ * 5 + flowerGridRand.nextInt(5);

                if (x == chosenFlowerX && z == chosenFlowerZ && flowerGridRand.nextDouble() < 0.10) {
                    generateFlowerCluster(level, flowerGridRand, x, z, minX, maxX, minZ, maxZ);
                }
            }
        }
    }

    // ★ ПОВАЛЕННОЕ ДЕРЕВО С ГРИБАМИ И СВИСАЮЩИМИ КОРНЯМИ ★
    private void generateFallenTree(WorldGenLevel level, RandomSource random, BlockPos pos, boolean isOak) {
        Direction.Axis axis = random.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z;
        int length = 3 + random.nextInt(4);

        BlockState logState = (isOak ? Blocks.OAK_LOG : Blocks.BIRCH_LOG)
                .defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, axis);

        for (int i = 0; i < length; i++) {
            BlockPos logPos = axis == Direction.Axis.X ? pos.east(i) : pos.south(i);

            BlockState current = level.getBlockState(logPos);
            if (!current.isAir() && !current.is(Blocks.GRASS) && !current.is(Blocks.FERN)
                    && !current.is(Blocks.LARGE_FERN) && !current.is(Blocks.TALL_GRASS)) {
                break;
            }

            level.setBlock(logPos, logState, 2);

            // Грибы на стволе
            if (random.nextDouble() < 0.30) {
                BlockPos mushroomPos = logPos.above();
                if (level.getBlockState(mushroomPos).isAir()) {
                    BlockState mushroom = random.nextBoolean()
                            ? Blocks.RED_MUSHROOM.defaultBlockState()
                            : Blocks.BROWN_MUSHROOM.defaultBlockState();
                    level.setBlock(mushroomPos, mushroom, 2);
                }
            }

            // ★ СВИСАЮЩИЕ КОРНИ: если под бревном нет блоков ★
            BlockPos belowLog = logPos.below();
            BlockState belowState = level.getBlockState(belowLog);
            if (belowState.isAir() && random.nextDouble() < 0.60) {
                level.setBlock(belowLog, Blocks.HANGING_ROOTS.defaultBlockState(), 2);
            }
        }
    }

    // ★ КЛАСТЕР ИЗ 5-10 ЦВЕТОВ (12 видов) ★
    private void generateFlowerCluster(WorldGenLevel level, Random rand, int centerX, int centerZ,
                                       int minX, int maxX, int minZ, int maxZ) {
        int clusterSize = 5 + rand.nextInt(6); // 5-10 цветов
        int flowerTypeIndex = rand.nextInt(12);
        BlockState flower = getFlowerByType(flowerTypeIndex);

        for (int i = 0; i < clusterSize; i++) {
            int fx = centerX + rand.nextInt(5) - 2;
            int fz = centerZ + rand.nextInt(5) - 2;

            // Не выходим за пределы чанка
            if (fx < minX || fx > maxX || fz < minZ || fz > maxZ) continue;
            if (fz >= -30) continue;
            if (isInRiverZone(fx, fz)) continue;

            double noise = terrainNoise.noise(fx * 0.04, 0, fz * 0.04);
            int surfaceY = FLOOR_Y + (int)(noise * 5);
            BlockPos flowerPos = new BlockPos(fx, surfaceY + 1, fz);

            BlockPos below = flowerPos.below();
            BlockState belowState = level.getBlockState(below);
            if ((belowState.is(Blocks.GRASS_BLOCK) || belowState.is(Blocks.DIRT))
                    && level.getBlockState(flowerPos).isAir()) {
                level.setBlock(flowerPos, flower, 2);
            }
        }
    }

    // ★ 12 ВИДОВ ЦВЕТОВ ★
    private BlockState getFlowerByType(int index) {
        switch (index) {
            case 0:  return Blocks.DANDELION.defaultBlockState();
            case 1:  return Blocks.POPPY.defaultBlockState();
            case 2:  return Blocks.BLUE_ORCHID.defaultBlockState();
            case 3:  return Blocks.ALLIUM.defaultBlockState();
            case 4:  return Blocks.AZURE_BLUET.defaultBlockState();
            case 5:  return Blocks.RED_TULIP.defaultBlockState();
            case 6:  return Blocks.ORANGE_TULIP.defaultBlockState();
            case 7:  return Blocks.WHITE_TULIP.defaultBlockState();
            case 8:  return Blocks.PINK_TULIP.defaultBlockState();
            case 9:  return Blocks.OXEYE_DAISY.defaultBlockState();
            case 10: return Blocks.CORNFLOWER.defaultBlockState();
            case 11: return Blocks.LILY_OF_THE_VALLEY.defaultBlockState();
            default: return Blocks.POPPY.defaultBlockState();
        }
    }
    private volatile double[][] riverCurvePoints = null;
    private volatile long riverSeedUsed = Long.MIN_VALUE;

    private static final double BRIDGE_MIN_WIDTH = 18.0;
    private static final double BRIDGE_MAX_WIDTH = 10.0;
    private static final double BRIDGE_BANK_HALF_WIDTH = 3.0;
    private static final double BRIDGE_LAND_MARGIN = 2.0;
    private static final double BRIDGE_MAX_CURVE_DEVIATION = 1.5;


    private void ensureRiverCurve() {
        long currentSeed = getEffectiveRiverSeed();

        double[][] existing = riverCurvePoints;
        if (existing != null && riverSeedUsed == currentSeed) {
            return;
        }

        synchronized (generationLock) {
            existing = riverCurvePoints;
            if (existing != null && riverSeedUsed == currentSeed) {
                return;
            }

            if (featureNoise == null) {
                featureNoise = new ImprovedNoise(
                        new net.minecraft.world.level.levelgen.LegacyRandomSource(currentSeed ^ 0x123456789ABCDEFL)
                );
            }

            ImprovedNoise localFeatureNoise = featureNoise;

            long riverSeed = currentSeed ^ 0x5EEDL;
            Random riverRand = new Random(riverSeed);

            int R = GLADE_RADIUS;

            // === 1. ВЫБОР УГЛА А (старт) ===
            int startCorner = riverRand.nextInt(4);
            double startX, startZ;

            switch (startCorner) {
                case 0:
                    startX = -R + 2;
                    startZ = -R + 2;
                    break;
                case 1:
                    startX = R - 2;
                    startZ = -R + 2;
                    break;
                case 2:
                    startX = R - 2;
                    startZ = R - 2;
                    break;
                default:
                    startX = -R + 2;
                    startZ = R - 2;
                    break;
            }

            // === 2. ВЫБОР ТОЧКИ Б (противоположная стена) ===
            double endX, endZ;
            int minPassageDist = 15;

            int wallChoice = riverRand.nextInt(2);
            boolean isHorizontalWall;
            int wallSign;

            switch (startCorner) {
                case 0: // Северо-запад → восточная или южная
                    if (wallChoice == 0) {
                        endX = R - 2;
                        endZ = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    } else {
                        endZ = R - 2;
                        endX = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    }
                    break;

                case 1: // Северо-восток → западная или южная
                    if (wallChoice == 0) {
                        endX = -R + 2;
                        endZ = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    } else {
                        endZ = R - 2;
                        endX = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    }
                    break;

                case 2: // Юго-восток → западная или северная
                    if (wallChoice == 0) {
                        endX = -R + 2;
                        endZ = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    } else {
                        endZ = -R + 2;
                        endX = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    }
                    break;

                default: // Юго-запад → восточная или северная
                    if (wallChoice == 0) {
                        endX = R - 2;
                        endZ = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    } else {
                        endZ = -R + 2;
                        endX = riverRand.nextBoolean()
                                ? minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5)
                                : -(minPassageDist + riverRand.nextDouble() * (R - minPassageDist - 5));
                    }
                    break;
            }

            // === 3. ПОСТРОЕНИЕ ПУТИ ===
            int pointCount = 80;
            double[][] points = new double[pointCount][2];

            double currentX = startX;
            double currentZ = startZ;

            double centerAvoidRadius = 40.0;

            for (int i = 0; i < pointCount; i++) {
                points[i][0] = currentX;
                points[i][1] = currentZ;

                if (i == pointCount - 1) break;

                double attractX = endX - currentX;
                double attractZ = endZ - currentZ;
                double attractDist = Math.sqrt(attractX * attractX + attractZ * attractZ);

                if (attractDist > 0.001) {
                    attractX /= attractDist;
                    attractZ /= attractDist;
                }

                double repelCenterX = 0, repelCenterZ = 0;
                double centerDist = Math.sqrt(currentX * currentX + currentZ * currentZ);

                if (centerDist < centerAvoidRadius && centerDist > 0.001) {
                    double t = 1.0 - centerDist / centerAvoidRadius;
                    double strength = t * t * 6.0;
                    repelCenterX = (currentX / centerDist) * strength;
                    repelCenterZ = (currentZ / centerDist) * strength;
                }

                double repelWallX = 0, repelWallZ = 0;

                if (attractDist > 10.0) {
                    double wallMargin = 8.0;

                    double distRight = (R - 2) - currentX;
                    double distLeft = currentX - (-R + 2);
                    double distUp = (R - 2) - currentZ;
                    double distDown = currentZ - (-R + 2);

                    double minWallDist = Math.min(Math.min(distRight, distLeft), Math.min(distUp, distDown));

                    if (minWallDist < wallMargin) {
                        double strength = (1.0 - minWallDist / wallMargin) * 2.5;

                        if (minWallDist == distRight) repelWallX = -strength;
                        else if (minWallDist == distLeft) repelWallX = strength;
                        else if (minWallDist == distUp) repelWallZ = -strength;
                        else repelWallZ = strength;
                    }
                }

                double totalX = attractX * 1.5 + repelCenterX + repelWallX;
                double totalZ = attractZ * 1.5 + repelCenterZ + repelWallZ;

                double noiseScale = 0.04;
                double bendX = localFeatureNoise.noise(currentX * noiseScale, currentZ * noiseScale, 500.0);
                double bendZ = localFeatureNoise.noise(currentX * noiseScale, currentZ * noiseScale, 600.0);

                double progress = (double) i / (pointCount - 1);
                double bendMultiplier = Math.sin(progress * Math.PI) * 3.0;

                totalX += bendX * bendMultiplier;
                totalZ += bendZ * bendMultiplier;

                double totalDist = Math.sqrt(totalX * totalX + totalZ * totalZ);

                if (totalDist > 0.001) {
                    totalX /= totalDist;
                    totalZ /= totalDist;
                }

                double stepSize = attractDist / (pointCount - 1 - i);
                stepSize = Math.min(stepSize, attractDist);
                stepSize = Math.min(stepSize, 4.0);

                currentX += totalX * stepSize;
                currentZ += totalZ * stepSize;

                currentX = Math.max(-R + 3, Math.min(R - 3, currentX));
                currentZ = Math.max(-R + 3, Math.min(R - 3, currentZ));
            }

            points[pointCount - 1][0] = endX;
            points[pointCount - 1][1] = endZ;

            riverCurvePoints = points;
            riverSeedUsed = currentSeed;
        }
    }

    private double distanceToRiverCurve(double x, double z) {
        ensureRiverCurve();

        double[][] points = riverCurvePoints;
        if (points == null) {
            return Double.MAX_VALUE;
        }

        double minDistSq = Double.MAX_VALUE;

        for (int i = 0; i < points.length - 1; i++) {
            double x1 = points[i][0];
            double z1 = points[i][1];

            double x2 = points[i + 1][0];
            double z2 = points[i + 1][1];

            double dx = x2 - x1;
            double dz = z2 - z1;

            double lenSq = dx * dx + dz * dz;

            double t = 0;

            if (lenSq > 0.0001) {
                t = ((x - x1) * dx + (z - z1) * dz) / lenSq;
                t = Math.max(0.0, Math.min(1.0, t));
            }

            double projX = x1 + t * dx;
            double projZ = z1 + t * dz;

            double distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);

            if (distSq < minDistSq) {
                minDistSq = distSq;
            }
        }

        return Math.sqrt(minDistSq);
    }
    // ★ РАССТОЯНИЕ ДО БЛИЖАЙШЕГО ПРОХОДА В ГЛЕЙД ★
    private double getPassageBlendFactor(int x, int z) {
        int R = GLADE_RADIUS;
        int halfW = 7;       // половина ширины прохода
        int blendRadius = 9; // радиус зоны сглаживания

        double distNorth = distanceToPassage(x, z, 0, -R, true);
        double distSouth = distanceToPassage(x, z, 0,  R, true);
        double distWest  = distanceToPassage(x, z, -R, 0, false);
        double distEast  = distanceToPassage(x, z,  R, 0, false);

        double minDist = Math.min(Math.min(distNorth, distSouth),
                Math.min(distWest, distEast));

        if (minDist >= blendRadius) return 0.0;
        return 1.0 - (minDist / blendRadius);
    }

    private double distanceToPassage(int x, int z, int px, int pz, boolean alongX) {
        int halfW = 7;
        if (alongX) {
            // Проход ориентирован вдоль оси X (север/юг)
            if (Math.abs(x - px) <= halfW) {
                return Math.abs(z - pz);
            } else {
                int dx = Math.abs(x - px) - halfW;
                int dz = Math.abs(z - pz);
                return Math.sqrt((double) dx * dx + (double) dz * dz);
            }
        } else {
            // Проход ориентирован вдоль оси Z (запад/восток)
            if (Math.abs(z - pz) <= halfW) {
                return Math.abs(x - px);
            } else {
                int dx = Math.abs(x - px);
                int dz = Math.abs(z - pz) - halfW;
                return Math.sqrt((double) dx * dx + (double) dz * dz);
            }
        }
    }
    private StructureBlendResult getStructureBlendResult(int x, int z) {
        double maxBlend = 0.0;
        int targetY = FLOOR_Y;
        boolean core = false;

        for (StructureGenerator.GladeStructureInfo info : StructureGenerator.getGladeStructureInfos()) {
            double dist = info.distanceToFootprint(x, z);

            // Внутри самого пятна застройки + маленький запас — жёсткая зона без воды.
            if (dist <= 1.0) {
                core = true;
            }

            if (dist < info.blendRadius) {
                double d = Math.max(0.0, dist);
                double blend = 1.0 - (d / info.blendRadius);
                blend = blend * blend * (3.0 - 2.0 * blend);

                if (blend > maxBlend) {
                    maxBlend = blend;
                    targetY = info.origin.getY();
                }
            }
        }

        // Радиус сглаживания лифта.
        // Применяется только если рядом нет структуры глейда.
        if (maxBlend <= 0.0) {
            double dist0 = Math.sqrt((double) x * x + (double) z * z);
            int liftBlendRadius = 30; // ★ РАДИУС СГЛАЖИВАНИЯ ЛИФТА

            if (dist0 < liftBlendRadius) {
                double blend0 = 1.0 - (dist0 / liftBlendRadius);
                blend0 = blend0 * blend0 * (3.0 - 2.0 * blend0);

                if (blend0 > maxBlend) {
                    maxBlend = blend0;
                    targetY = FLOOR_Y;
                }
            }
        }

        return new StructureBlendResult(maxBlend, targetY, core);
    }

    private double getStructureBlendFactor(int x, int z) {
        return getStructureBlendResult(x, z).factor;
    }

    private void initializeSeed(RandomState random) {
        if (seedInitialized) return;

        synchronized (generationLock) {
            if (seedInitialized) return;

            long s = 0;

            if (WorldSeedHolder.isSeedLoaded()) {
                s = WorldSeedHolder.getWorldSeed();
            }

            if (s == 0) {
                net.minecraft.world.level.biome.Climate.TargetPoint point = random.sampler().sample(0, 0, 0);
                s = point.temperature() ^ point.continentalness();
            }

            if (s == 0) {
                s = 0x9E3779B97F4A7C15L;
            }

            this.seed = s;

            this.terrainNoise = new ImprovedNoise(
                    new net.minecraft.world.level.levelgen.LegacyRandomSource(seed)
            );

            this.featureNoise = new ImprovedNoise(
                    new net.minecraft.world.level.levelgen.LegacyRandomSource(seed ^ 0x123456789ABCDEFL)
            );

            seedInitialized = true;
        }
    }
    private long getEffectiveRiverSeed() {
        if (WorldSeedHolder.isSeedLoaded()) {
            long s = WorldSeedHolder.getWorldSeed();
            if (s != 0) return s;
        }

        if (this.seed != 0) {
            return this.seed;
        }

        return 0x9E3779B97F4A7C15L;
    }

    private void invalidateRiverIfSeedChanged() {
        long currentSeed = getEffectiveRiverSeed();

        if (riverCurvePoints != null && riverSeedUsed != currentSeed) {
            synchronized (generationLock) {
                if (riverCurvePoints != null && riverSeedUsed != currentSeed) {
                    riverCurvePoints = null;
                }
            }
        }
    }
    private void ensureDecorationSeed(WorldGenLevel level) {
        if (seedInitialized && terrainNoise != null && featureNoise != null) {
            return;
        }

        synchronized (generationLock) {
            if (seedInitialized && terrainNoise != null && featureNoise != null) {
                return;
            }

            long s = level.getSeed();

            if (s == 0 && WorldSeedHolder.isSeedLoaded()) {
                s = WorldSeedHolder.getWorldSeed();
            }

            if (s == 0) {
                s = this.seed;
            }

            if (s == 0) {
                s = 0x9E3779B97F4A7C15L;
            }

            this.seed = s;

            this.terrainNoise = new ImprovedNoise(
                    new net.minecraft.world.level.levelgen.LegacyRandomSource(seed)
            );

            this.featureNoise = new ImprovedNoise(
                    new net.minecraft.world.level.levelgen.LegacyRandomSource(seed ^ 0x123456789ABCDEFL)
            );

            this.seedInitialized = true;
        }
    }

    // ★ ПОИСК ТОЧКИ, ГДЕ РЕКА УЖЕ ИМЕЕТ ШИРИНУ 10-13 БЛОКОВ ★
    private int findNaturalNarrowRiverPoint(double[][] points, int margin) {
        for (int i = margin; i < points.length - margin; i++) {
            int px = (int) Math.round(points[i][0]);
            int pz = (int) Math.round(points[i][1]);

            double waterWidth = computeBaseWaterHalfWidth(px, pz) * 2.0;

            if (waterWidth >= BRIDGE_MIN_WIDTH && waterWidth <= BRIDGE_MAX_WIDTH) {
                return i;
            }
        }
        return -1;
    }

    // ★ ПОИСК САМОГО ПРЯМОГО УЧАСТКА РЕКИ (вынесено из старого initializeBridge) ★
    private int findStraightestSegment(double[][] points, int windowSize, int margin) {
        double bestStraightness = Double.MAX_VALUE;
        int bestCenterIdx = points.length / 2; // Фоллбэк — середина

        for (int i = margin; i <= points.length - windowSize - margin; i++) {
            double dirX = points[i + windowSize - 1][0] - points[i][0];
            double dirZ = points[i + windowSize - 1][1] - points[i][1];
            double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
            if (dirLen < 0.001) continue;

            double ndx = dirX / dirLen;
            double ndz = dirZ / dirLen;

            double totalDeviation = 0;
            for (int j = i + 1; j < i + windowSize - 1; j++) {
                double vx = points[j][0] - points[i][0];
                double vz = points[j][1] - points[i][1];
                double proj = vx * ndx + vz * ndz;
                double closestX = points[i][0] + ndx * proj;
                double closestZ = points[i][1] + ndz * proj;
                double devX = points[j][0] - closestX;
                double devZ = points[j][1] - closestZ;
                totalDeviation += Math.sqrt(devX * devX + devZ * devZ);
            }

            if (totalDeviation < bestStraightness) {
                bestStraightness = totalDeviation;
                bestCenterIdx = i + windowSize / 2;
            }
        }

        return bestCenterIdx;
    }
    private void initializeGladeStructures() {
        StructureGenerator.clearGladeStructures();
        StructureGenerator.preloadGladeSizes();

        if (terrainNoise == null || featureNoise == null) {
            return;
        }

        Random rand = new Random(this.seed ^ 0xDEADBEEFCAFEL);

        int fermaRadius   = StructureGenerator.getGladeStructurePlacementRadius("ferma", 12);
        int banfairRadius = StructureGenerator.getGladeStructurePlacementRadius("banfair", 12);
        int lagerRadius   = StructureGenerator.getGladeStructurePlacementRadius("lager", 10);
        int towerRadius   = StructureGenerator.getGladeStructurePlacementRadius("tower", 7);

        // ★ ГАРАНТИРОВАННОЕ размещение — все 4 структуры будут поставлены ★
        placeGladeStructureGuaranteed("ferma",   rand, 0, fermaRadius,   fermaRadius + 12, 4);
        placeGladeStructureGuaranteed("banfair", rand, 1, banfairRadius, banfairRadius + 12, 4);
        placeGladeStructureGuaranteed("lager",   rand, 2, lagerRadius,   lagerRadius + 10, 4);
        placeTowerStructureGuaranteed(rand, towerRadius);
    }

    /**
     * ★ ТОЧНЫЙ РАСЧЕТ ВЫСОТЫ ПОВЕРХНОСТИ ★
     * На 100% копирует логику из generateNaturalTerrain, чтобы найти реальный блок травы.
     */
    private int getExactTerrainHeight(int x, int z) {
        double noise = terrainNoise.noise(x * 0.04, 0, z * 0.04);
        int terrainHeight = FLOOR_Y + (int)(noise * 5);

        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist >= 30 && dist <= 55) {
            double hillNoise1 = featureNoise.noise(x * 0.04, 0, z * 0.04);
            double hillNoise2 = terrainNoise.noise(x * 0.08, 0, z * 0.08);
            double combinedNoise = (hillNoise1 + hillNoise2) * 0.5;
            if (combinedNoise > 0.4) {
                double heightFactor = (combinedNoise - 0.4) / 0.6;
                int addedHeight = (int)(heightFactor * 6.0);
                addedHeight = Math.max(0, Math.min(addedHeight, 6));
                terrainHeight += addedHeight;
            }
        }
        return terrainHeight;
    }

    private int[] findHighestHill() {
        int bestX = 0, bestZ = 0, maxY = FLOOR_Y;

        // Грубый поиск (шаг 2 для оптимизации)
        for (int x = -GLADE_RADIUS; x <= GLADE_RADIUS; x += 2) {
            for (int z = -GLADE_RADIUS; z <= GLADE_RADIUS; z += 2) {
                int dist = Math.max(Math.abs(x), Math.abs(z));
                if (dist < 30 || dist > 55) continue; // Ищем только в зоне холмов

                int y = getExactTerrainHeight(x, z);
                if (y > maxY) {
                    maxY = y;
                    bestX = x;
                    bestZ = z;
                }
            }
        }

        // Точный поиск вокруг найденной точки (шаг 1)
        int finalX = bestX, finalZ = bestZ;
        for (int x = bestX - 2; x <= bestX + 2; x++) {
            for (int z = bestZ - 2; z <= bestZ + 2; z++) {
                int dist = Math.max(Math.abs(x), Math.abs(z));
                if (dist < 30 || dist > 55) continue;
                int y = getExactTerrainHeight(x, z);
                if (y > maxY) {
                    maxY = y;
                    finalX = x;
                    finalZ = z;
                }
            }
        }

        return new int[]{finalX, maxY, finalZ};
    }

    /**
     * ★ ПРОВЕРКА: ПОПАДАЕТ ЛИ ТОЧКА В ЗОНУ СТРУКТУРЫ ★
     * Нужно, чтобы деревья и цветы не спавнились внутри фундаментов.
     */
    private boolean isInsideStructureZone(int x, int z) {
        return StructureGenerator.isInsideGladeStructureZone(x, z, 2);
    }
    private static class StructureBlendResult {
        final double factor;
        final int targetY;
        final boolean core;

        StructureBlendResult(double factor, int targetY, boolean core) {
            this.factor = factor;
            this.targetY = targetY;
            this.core = core;
        }
    }
    private int getGladeStructureMinDist() {
        return Math.max(22, GLADE_RADIUS / 4);
    }

    /**
     * ★ ГАРАНТИРОВАННОЕ размещение структуры глейда ★
     * Каскад: обычный поиск → relaxed → другие квадранты → emergency → форс.
     * Структура будет поставлена ВСЕГДА.
     */
    private void placeGladeStructureGuaranteed(
            String name,
            Random rand,
            int quadrant,
            int fallbackRadius,
            int fallbackBlendRadius,
            int margin
    ) {
        Rotation rotation = Rotation.values()[rand.nextInt(Rotation.values().length)];

        // Попытка 1: обычный поиск
        BlockPos pos = tryFindGladeStructurePosition(
                name, rand, quadrant, rotation, fallbackRadius, margin, 10
        );

        // Попытка 2: relaxed (мягкие требования)
        if (pos == null) {
            pos = tryFindGladeStructurePositionRelaxed(
                    name, rand, quadrant, rotation, fallbackRadius, margin
            );
        }

        // Попытка 3: другие квадранты
        if (pos == null) {
            for (int q = 0; q < 4 && pos == null; q++) {
                if (q == quadrant) continue;
                pos = tryFindGladeStructurePositionRelaxed(
                        name, rand, q, rotation, fallbackRadius, margin
                );
            }
        }

        // Попытка 4: emergency — спираль по всему глейду
        if (pos == null) {
            pos = findEmergencyGladePosition(name, rotation, fallbackRadius, margin);
        }

        // Попытка 5: жёсткий фоллбэк по квадранту
        if (pos == null) {
            pos = getForcedFallbackPosition(quadrant);
            System.out.println("[LabyrinthChunkGenerator] FORCED fallback for '"
                    + name + "' at " + pos);
        }

        StructureGenerator.addGladeStructure(
                name, pos, rotation, fallbackRadius, fallbackBlendRadius
        );

        System.out.println("[LabyrinthChunkGenerator] Placed glade structure '" + name
                + "' at " + pos + " rot=" + rotation);
    }

    private void placeTowerStructureGuaranteed(Random rand, int fallbackRadius) {
        int margin = 4;
        int minGap = 10;

        Rotation rotation = Rotation.values()[rand.nextInt(Rotation.values().length)];

        // Попытка 1: обычный поиск (ищем на холмах)
        BlockPos pos = findBestTowerPosition(
                "tower", rotation, fallbackRadius, margin, minGap
        );

        // Попытка 2: обычный поиск в квадранте 3
        if (pos == null) {
            pos = tryFindGladeStructurePosition(
                    "tower", rand, 3, rotation, fallbackRadius, margin, minGap
            );
        }

        // Попытка 3: relaxed
        if (pos == null) {
            pos = tryFindGladeStructurePositionRelaxed(
                    "tower", rand, 3, rotation, fallbackRadius, margin
            );
        }

        // Попытка 4: emergency
        if (pos == null) {
            pos = findEmergencyGladePosition("tower", rotation, fallbackRadius, margin);
        }

        // Попытка 5: форс
        if (pos == null) {
            pos = getForcedFallbackPosition(3);
            System.out.println("[LabyrinthChunkGenerator] FORCED fallback for tower at " + pos);
        }

        StructureGenerator.addGladeStructure(
                "tower", pos, rotation, fallbackRadius, fallbackRadius + 12
        );

        System.out.println("[LabyrinthChunkGenerator] Placed tower at " + pos + " rot=" + rotation);
    }

    private BlockPos tryFindGladeStructurePosition(
            String name,
            Random rand,
            int quadrant,
            Rotation rotation,
            int fallbackRadius,
            int margin,
            int minGap
    ) {
        int minDist = getGladeStructureMinDist();
        int maxDist = GLADE_RADIUS - 5;

        int distanceRange = Math.max(1, maxDist - minDist);
        double baseAngle = quadrant * Math.PI / 2.0 + Math.PI / 4.0;

        for (int attempt = 0; attempt < 220; attempt++) {
            double angle = baseAngle + (rand.nextDouble() * 2.0 - 1.0) * (Math.PI / 4.0) * 0.8;
            int distance = minDist + rand.nextInt(distanceRange);

            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            if (isSafeForGladeStructure(name, x, z, rotation, fallbackRadius, margin, minGap)) {
                return new BlockPos(x, getPlacementTerrainHeight(x, z), z);
            }
        }

        for (int attempt = 0; attempt < 260; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            int distance = minDist + rand.nextInt(distanceRange);

            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            if (isSafeForGladeStructure(name, x, z, rotation, fallbackRadius, margin, minGap)) {
                return new BlockPos(x, getPlacementTerrainHeight(x, z), z);
            }
        }

        return findFallbackGladePosition(name, rotation, fallbackRadius, margin, minGap);
    }

    private BlockPos findFallbackGladePosition(
            String name,
            Rotation rotation,
            int fallbackRadius,
            int margin,
            int minGap
    ) {
        int minDist = getGladeStructureMinDist();
        int maxDist = GLADE_RADIUS - 5;

        for (int distance = maxDist; distance >= minDist; distance -= 4) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
                double angle = Math.toRadians(angleDeg);

                int x = (int) Math.round(Math.cos(angle) * distance);
                int z = (int) Math.round(Math.sin(angle) * distance);

                if (isSafeForGladeStructure(
                        name,
                        x,
                        z,
                        rotation,
                        fallbackRadius,
                        margin,
                        minGap
                )) {
                    return new BlockPos(
                            x,
                            getPlacementTerrainHeight(x, z),
                            z
                    );
                }
            }
        }

        return null;
    }


    private boolean quickStructureSafety(int x, int z, int radius, int minGap) {
        int dist = Math.max(Math.abs(x), Math.abs(z));

        int minDist = getGladeStructureMinDist();
        int maxDist = GLADE_RADIUS - 5;

        if (dist < minDist || dist > maxDist) {
            return false;
        }

        if (getPassageBlendFactor(x, z) > 0.25) {
            return false;
        }

        if (StructureGenerator.isTooCloseToGladeStructures(x, z, radius, minGap)) {
            return false;
        }

        return !isInRiverZone(x, z, 2.0);
    }

    private int getPlacementTerrainHeight(int x, int z) {
        int terrainHeight = getExactTerrainHeight(x, z);

        double passageBlend = getPassageBlendFactor(x, z);
        if (passageBlend > 0.0) {
            double smoothBlend = passageBlend * passageBlend * (3.0 - 2.0 * passageBlend);
            terrainHeight = (int)(terrainHeight * (1.0 - smoothBlend) + FLOOR_Y * smoothBlend);
        }

        return Math.max(FLOOR_Y, terrainHeight);
    }

    private BlockPos findBestTowerPosition(String name, Rotation rotation, int fallbackRadius, int margin, int minGap) {
        int minDist = getGladeStructureMinDist();
        int maxDist = GLADE_RADIUS - 5;

        BlockPos best = null;
        int bestY = Integer.MIN_VALUE;

        // Сначала ищем на холмах.
        for (int x = -GLADE_RADIUS; x <= GLADE_RADIUS; x += 4) {
            for (int z = -GLADE_RADIUS; z <= GLADE_RADIUS; z += 4) {
                int dist = Math.max(Math.abs(x), Math.abs(z));

                if (dist < 30 || dist > 55) {
                    continue;
                }

                if (!quickStructureSafety(x, z, fallbackRadius, minGap)) {
                    continue;
                }

                int y = getPlacementTerrainHeight(x, z);

                if (y <= bestY) {
                    continue;
                }

                if (!isSafeForGladeStructure(name, x, z, rotation, fallbackRadius, margin, minGap)) {
                    continue;
                }

                bestY = y;
                best = new BlockPos(x, y, z);
            }
        }

        if (best != null) {
            return best;
        }

        // Если холмов нет — ищем просто безопасную высокую точку.
        for (int x = -GLADE_RADIUS; x <= GLADE_RADIUS; x += 5) {
            for (int z = -GLADE_RADIUS; z <= GLADE_RADIUS; z += 5) {
                int dist = Math.max(Math.abs(x), Math.abs(z));

                if (dist < minDist || dist > maxDist) {
                    continue;
                }

                if (!quickStructureSafety(x, z, fallbackRadius, minGap)) {
                    continue;
                }

                int y = getPlacementTerrainHeight(x, z);

                if (y <= bestY) {
                    continue;
                }

                if (!isSafeForGladeStructure(name, x, z, rotation, fallbackRadius, margin, minGap)) {
                    continue;
                }

                bestY = y;
                best = new BlockPos(x, y, z);
            }
        }

        return best;
    }

    private int getGladeStructureMaxDist(int radius) {
        return Math.max(getGladeStructureMinDist() + 1, GLADE_RADIUS - radius - 8);
    }

    private boolean isSafeForGladeStructure(String name, int x, int z, Rotation rotation, int fallbackRadius, int margin, int minGap) {
        if (!quickStructureSafety(x, z, fallbackRadius, minGap)) {
            return false;
        }

        // ★ ГЛАВНАЯ ЗАЩИТА ОТ ПЕРЕСЕЧЕНИЯ С ДРУГИМИ СТРУКТУРАМИ ★
        if (StructureGenerator.isStructurePlacementBlocked(
                name,
                new BlockPos(x, 0, z),
                rotation,
                fallbackRadius,
                margin
        )) {
            return false;
        }

        int[] aabb = StructureGenerator.getFootprintAabb(
                name,
                new BlockPos(x, 0, z),
                rotation,
                fallbackRadius,
                margin
        );

        int minDist = getGladeStructureMinDist();

        // Проверяем всё фактическое пятно структуры.
        for (int sx = aabb[0]; sx <= aabb[1]; sx += 2) {
            for (int sz = aabb[2]; sz <= aabb[3]; sz += 2) {
                int sd = Math.max(Math.abs(sx), Math.abs(sz));

                if (sd < minDist || sd > GLADE_RADIUS - 5) {
                    return false;
                }

                if (isInRiverZone(sx, sz, margin)) {
                    return false;
                }
            }
        }

        return true;
    }
    /**
     * ★ RELAXED-ПОИСК: расширенный квадрант, шире диапазон, мягче проверки ★
     */
    private BlockPos tryFindGladeStructurePositionRelaxed(
            String name,
            Random rand,
            int quadrant,
            Rotation rotation,
            int fallbackRadius,
            int margin
    ) {
        int minDist = 14;                 // было max(22, R/4)
        int maxDist = GLADE_RADIUS - 3;   // было R - 5
        int distanceRange = Math.max(1, maxDist - minDist);
        double baseAngle = quadrant * Math.PI / 2.0 + Math.PI / 4.0;

        // Фаза 1: расширенный целевой квадрант (±90°)
        for (int attempt = 0; attempt < 400; attempt++) {
            double angle = baseAngle + (rand.nextDouble() * 2.0 - 1.0) * (Math.PI / 2.0);
            int distance = minDist + rand.nextInt(distanceRange);

            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            if (isSafeRelaxed(name, x, z, rotation, fallbackRadius, margin, 4)) {
                return new BlockPos(x, getPlacementTerrainHeight(x, z), z);
            }
        }

        // Фаза 2: любой угол
        for (int attempt = 0; attempt < 400; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            int distance = minDist + rand.nextInt(distanceRange);

            int x = (int) Math.round(Math.cos(angle) * distance);
            int z = (int) Math.round(Math.sin(angle) * distance);

            if (isSafeRelaxed(name, x, z, rotation, fallbackRadius, margin, 4)) {
                return new BlockPos(x, getPlacementTerrainHeight(x, z), z);
            }
        }

        return null;
    }

    /**
     * ★ МЯГКАЯ проверка безопасности ★
     * Не проверяем passageBlend и близость к реке (margin=1).
     */
    private boolean isSafeRelaxed(
            String name,
            int x,
            int z,
            Rotation rotation,
            int fallbackRadius,
            int margin,
            int minGap
    ) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > GLADE_RADIUS - 3) return false;
        if (dist < 12) return false;

        // Не пересекаемся с другими структурами (это критично!)
        if (StructureGenerator.isTooCloseToGladeStructures(
                x, z, fallbackRadius, minGap)) {
            return false;
        }
        if (StructureGenerator.isStructurePlacementBlocked(
                name, new BlockPos(x, 0, z), rotation, fallbackRadius, margin)) {
            return false;
        }

        // Слабый запрет на реку — только самая сердцевина
        if (isInRiverZone(x, z, 1.0)) return false;

        return true;
    }

    /**
     * ★ EMERGENCY: спираль по всему глейду с минимальными требованиями ★
     * Требование только одно: не пересекаться с уже поставленными структурами.
     */
    private BlockPos findEmergencyGladePosition(
            String name,
            Rotation rotation,
            int fallbackRadius,
            int margin
    ) {
        int minDist = 14;
        int maxDist = GLADE_RADIUS - 3;

        // Сначала пробуем с защитой от пересечений
        for (int distance = maxDist; distance >= minDist; distance -= 2) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 5) {
                double angle = Math.toRadians(angleDeg);
                int x = (int) Math.round(Math.cos(angle) * distance);
                int z = (int) Math.round(Math.sin(angle) * distance);

                if (StructureGenerator.isStructurePlacementBlocked(
                        name, new BlockPos(x, 0, z), rotation, fallbackRadius, margin)) {
                    continue;
                }
                if (StructureGenerator.isTooCloseToGladeStructures(
                        x, z, fallbackRadius, 2)) {
                    continue;
                }

                System.out.println("[LabyrinthChunkGenerator] Emergency placement '"
                        + name + "' at " + x + "," + z);
                return new BlockPos(x, getPlacementTerrainHeight(x, z), z);
            }
        }

        // Самый крайний случай: игнорируем пересечения (лучше поставить поверх, чем не поставить)
        for (int distance = maxDist; distance >= minDist; distance -= 4) {
            for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
                double angle = Math.toRadians(angleDeg);
                int x = (int) Math.round(Math.cos(angle) * distance);
                int z = (int) Math.round(Math.sin(angle) * distance);

                System.out.println("[LabyrinthChunkGenerator] EMERGENCY (overlap-allowed) '"
                        + name + "' at " + x + "," + z);
                return new BlockPos(x, getPlacementTerrainHeight(x, z), z);
            }
        }

        return null;
    }

    /**
     * ★ Форс-позиция по квадранту (если вообще ничего не сработало) ★
     */
    private BlockPos getForcedFallbackPosition(int quadrant) {
        int r = Math.max(20, GLADE_RADIUS / 2);

        switch (quadrant) {
            case 0:  return new BlockPos( r, FLOOR_Y, -r);  // СВ
            case 1:  return new BlockPos( r, FLOOR_Y,  r);  // ЮВ
            case 2:  return new BlockPos(-r, FLOOR_Y,  r);  // ЮЗ
            default: return new BlockPos(-r, FLOOR_Y, -r);  // СЗ (tower)
        }
    }
}
