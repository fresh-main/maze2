package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

public class StructureGenerator {
    private static final List<GladeStructureInfo> gladeStructureInfos = new ArrayList<>();
    private static final String[] GLADE_STRUCTURE_NAMES = {"ferma", "banfair", "tower", "lager"};
    private static final Map<String, Vec3i> gladeSizeCache = new HashMap<>();
    private static final Map<String, BlockPos> gladePivotOffsets = new HashMap<>();
    private static volatile boolean gladeSizesLoaded = false;
    private static final Map<String, BlockPos> GLADE_PIVOT_OFFSETS = new HashMap<>();

    static {
        // ==========================================================
        // Если структура устанавливается НЕ за минимальный угол,
        // здесь нужно указать смещение точки установки относительно
        // минимального угла структуры.
        //
        // Например, если точка установки фермы находится:
        // на 24 блока восточнее минимального угла,
        // на 0 по Y,
        // на 6 блоков южнее минимального угла:
        //
        // GLADE_PIVOT_OFFSETS.put("ferma", new BlockPos(24, 0, 6));
        //
        // Если точка установки в центре структуры, например размер 49x13:
        // GLADE_PIVOT_OFFSETS.put("ferma", new BlockPos(24, 0, 6));
        // ==========================================================
    }

    // ★ КЛАСС ДЛЯ ХРАНЕНИЯ ЗАЩИЩЁННОЙ ЗОНЫ (AABB из целых чисел)
    public static class ProtectedRegion {
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;
        public final String structureName;

        public ProtectedRegion(String name, BlockPos origin, Vec3i size) {
            this.structureName = name;
            this.minX = origin.getX();
            this.minY = origin.getY();
            this.minZ = origin.getZ();
            this.maxX = origin.getX() + size.getX() - 1;
            this.maxY = origin.getY() + size.getY() - 1;
            this.maxZ = origin.getZ() + size.getZ() - 1;
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    public static class StructureData {
        public final StructurePlacement placement;
        public final BlockPos origin;
        public Vec3i size;
        public final boolean breakable;

        public StructureData(StructurePlacement placement, BlockPos origin) {
            this.placement = placement;
            this.origin = origin;
            this.breakable = placement.isBreakable();
        }

        public boolean intersectsChunk(int chunkX, int chunkZ) {
            int[] bounds = getWorldBounds();

            int cMinX = chunkX << 4;
            int cMaxX = cMinX + 15;
            int cMinZ = chunkZ << 4;
            int cMaxZ = cMinZ + 15;

            return cMaxX >= bounds[0]
                    && cMinX <= bounds[1]
                    && cMaxZ >= bounds[2]
                    && cMinZ <= bounds[3];
        }

        /**
         * Возвращает реальный мировой AABB структуры:
         * {minX, maxX, minZ, maxZ}
         */
        public int[] getWorldBounds() {
            if (size == null || size.getX() <= 0 || size.getZ() <= 0) {
                // Если размер ещё не загружен, берём запасную зону.
                // Для глайд-структур лучше переключить больше чанков,
                // чем потерять часть постройки.
                int pad = isGladeStructure(placement.getName()) ? 64 : 16;

                return new int[]{
                        origin.getX() - pad,
                        origin.getX() + pad,
                        origin.getZ() - pad,
                        origin.getZ() + pad
                };
            }

            int minXLocal = 0;
            int maxXLocal = size.getX() - 1;
            int minZLocal = 0;
            int maxZLocal = size.getZ() - 1;

            // Если у структуры указана опорная точка,
            // считаем границы относительно неё.
            BlockPos pivot = GLADE_PIVOT_OFFSETS.get(placement.getName());

            if (pivot != null) {
                minXLocal = -pivot.getX();
                maxXLocal = size.getX() - 1 - pivot.getX();
                minZLocal = -pivot.getZ();
                maxZLocal = size.getZ() - 1 - pivot.getZ();
            }

            return transformLocalBounds(
                    origin,
                    placement.getRotation(),
                    minXLocal,
                    maxXLocal,
                    minZLocal,
                    maxZLocal
            );
        }
    }

    private static final List<StructureData> structures = new ArrayList<>();
    // ★ СПИСОК ЗАЩИЩЁННЫХ ЗОН (заполняется при размещении структур)
    private static final List<ProtectedRegion> protectedRegions = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // ★ lift_1 — НЕЛЬЗЯ ломать (false)
        structures.add(new StructureData(
                new StructurePlacement("lift_1", "labyrinthmod", "lift_1",
                        new BlockPos(-16, -27, -12), false),
                new BlockPos(-16, -27, -12)
        ));

        // ★ lift_2 — НЕЛЬЗЯ ломать (false)
        structures.add(new StructureData(
                new StructurePlacement("lift_2", "labyrinthmod", "lift_2",
                        new BlockPos(-16, 11, -12), false),
                new BlockPos(-16, 11, -12)
        ));

    }

    public static void loadSizesIfNeeded(WorldGenLevel level) {
        for (StructureData data : structures) {
            if (data.size == null) {
                try {
                    StructureTemplateManager manager = level.getLevel().getServer().getStructureManager();
                    Optional<StructureTemplate> opt = manager.get(data.placement.getNbtLocation());
                    if (opt.isPresent()) {
                        data.size = opt.get().getSize();
                    } else {
                        data.size = new Vec3i(0, 0, 0);
                    }
                } catch (Exception e) {
                    data.size = new Vec3i(0, 0, 0);
                }
            }
        }
    }

    public static void placeStructuresInChunk(WorldGenLevel level, ChunkAccess chunk) {
        init();
        loadGladeSizes(level);
        loadSizesIfNeeded(level);
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        for (StructureData data : structures) {
            if (data.intersectsChunk(chunkX, chunkZ)) {
                data.placement.place(level, null);

                if (!data.breakable && data.size != null && data.size.getX() > 0) {
                    boolean alreadyRegistered = protectedRegions.stream()
                            .anyMatch(r -> r.structureName.equals(data.placement.getName()));

                    if (!alreadyRegistered) {
                        int[] bounds = data.getWorldBounds();

                        int height = data.size.getY() > 0 ? data.size.getY() : 80;

                        BlockPos min = new BlockPos(bounds[0], data.origin.getY(), bounds[2]);
                        Vec3i regionSize = new Vec3i(
                                Math.max(1, bounds[1] - bounds[0] + 1),
                                Math.max(1, height),
                                Math.max(1, bounds[3] - bounds[2] + 1)
                        );

                        protectedRegions.add(
                                new ProtectedRegion(data.placement.getName(), min, regionSize)
                        );

                        System.out.println("[StructureGenerator] Protected region registered: "
                                + data.placement.getName()
                                + " (Rot: " + data.placement.getRotation() + ")"
                                + " bounds=[" + bounds[0] + ", " + bounds[1] + "] "
                                + "[" + bounds[2] + ", " + bounds[3] + "]");
                    }
                }
            }
        }
    }

    // ★ ПУБЛИЧНЫЙ МЕТОД: проверяет, защищён ли блок от разрушения
    public static boolean isBlockProtected(BlockPos pos) {
        for (ProtectedRegion region : protectedRegions) {
            if (region.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    // ★ Для отладки
    public static List<ProtectedRegion> getProtectedRegions() {
        return protectedRegions;
    }
    public static void updateDverPosition(int currentGladeRadius) {
        structures.removeIf(data -> "dver_1".equals(data.placement.getName()));

        int dverX = -24;
        int dverY = 31;
        int dverZ = -(currentGladeRadius + 8);

        // ★ ДВЕРЬ 1 (Отрицательный Z) — ПОВОРОТ НА 90 ГРАДУСОВ (ПРИМЕР)
        structures.add(new StructureData(
                new StructurePlacement("dver_2", "labyrinthmod", "dver_2",
                        new BlockPos(dverX, dverY, dverZ), false, Rotation.NONE), // ★ ПОВОРОТ
                new BlockPos(dverX, dverY, dverZ)
        ));

        // ★ ДВЕРЬ 2 (Положительный Z) — ПОВОРОТ НА 180 ГРАДУСОВ (ЧТОБЫ СМОТРЕЛА В ЦЕНТР)
        structures.add(new StructureData(
                new StructurePlacement("dver_1", "labyrinthmod", "dver_1",
                        new BlockPos(dverX, dverY, -dverZ-9), false, Rotation.NONE), // ★ ПОВОРОТ
                new BlockPos(dverX, dverY, -dverZ-9)
        ));

        structures.add(new StructureData(
                new StructurePlacement("dver_4", "labyrinthmod", "dver_4",
                        new BlockPos(dverZ, dverY, dverX), false, Rotation.NONE), // ★ ПОВОРОТ
                new BlockPos(dverZ, dverY, dverX)
        ));

        // ★ ДВЕРЬ 2 (Положительный Z) — ПОВОРОТ НА 180 ГРАДУСОВ (ЧТОБЫ СМОТРЕЛА В ЦЕНТР)
        structures.add(new StructureData(
                new StructurePlacement("dver_3", "labyrinthmod", "dver_3",
                        new BlockPos(-dverZ-9, dverY, dverX), false, Rotation.NONE), // ★ ПОВОРОТ
                new BlockPos(-dverZ-9, dverY, dverX)
        ));


        System.out.println("[StructureGenerator] dver_1 dynamically positioned at Z=" + dverZ + " (Radius=" + currentGladeRadius + ")");
    }
    /**
     * ★ ДИНАМИЧЕСКОЕ РАЗМЕЩЕНИЕ МОСТА ★
     */
    public static void updateBridgePosition(int x, int y, int z, Rotation rotation) {
        // Удаляем предыдущий мост, если он был (на случай смены сида или перегенерации)
        structures.removeIf(data -> "most".equals(data.placement.getName()));

        // Добавляем новый мост (breakable = false, чтобы игроки не могли его сломать)
        structures.add(new StructureData(
                new StructurePlacement("most", "labyrinthmod", "most",
                        new BlockPos(x, y, z), false, rotation),
                new BlockPos(x, y, z)
        ));

        System.out.println("[StructureGenerator] Bridge 'most' dynamically positioned at "
                + x + "," + y + "," + z + " Rot: " + rotation);
    }
    // ★ СПИСОК КООРДИНАТ СТРУКТУР НА ПОЛЯНЕ (для сглаживания ландшафта и защиты от деревьев)
    private static final List<BlockPos> gladeStructureOrigins = new ArrayList<>();

    public static List<BlockPos> getGladeStructureOrigins() {
        return gladeStructureOrigins;
    }

    public static void clearGladeStructures() {
        for (String n : GLADE_STRUCTURE_NAMES) {
            structures.removeIf(d -> n.equals(d.placement.getName()));
            protectedRegions.removeIf(r -> n.equals(r.structureName));
        }

        gladeStructureOrigins.clear();
        gladeStructureInfos.clear();
    }

    public static void addGladeStructure(String name, BlockPos pos, Rotation rotation) {
        addGladeStructure(name, pos, rotation, 12, 24);
    }

    public static void addGladeStructure(String name, BlockPos pos, Rotation rotation, int fallbackRadius, int fallbackBlendRadius) {
        preloadGladeSizes();

        structures.removeIf(data -> name.equals(data.placement.getName()));

        structures.add(new StructureData(
                new StructurePlacement(name, "labyrinthmod", name, pos, false, rotation),
                pos
        ));

        gladeStructureOrigins.add(pos);
        gladeStructureInfos.removeIf(info -> info.name.equals(name));

        GladeStructureInfo info = new GladeStructureInfo(name, pos, rotation, fallbackRadius, fallbackBlendRadius);

        Vec3i cachedSize = gladeSizeCache.get(name);
        if (cachedSize != null) {
            info.setSize(cachedSize, getPivotOffset(name, cachedSize));
        }

        gladeStructureInfos.add(info);

        registerOrUpdateProtectedRegion(info);

        System.out.println("[StructureGenerator] Glade structure '" + name + "' added at " + pos
                + " Rot: " + rotation
                + " fallbackRadius=" + fallbackRadius
                + " blendRadius=" + info.blendRadius
                + " sizeLoaded=" + info.sizeLoaded);
    }


    public static class GladeStructureInfo {
        public final String name;
        public final BlockPos origin;
        public final Rotation rotation;

        public int fallbackRadius;
        public int blendRadius;

        public Vec3i size;
        public boolean sizeLoaded = false;

        public int localMinX;
        public int localMinZ;
        public int localMaxX;
        public int localMaxZ;

        public GladeStructureInfo(String name, BlockPos origin, Rotation rotation, int fallbackRadius, int fallbackBlendRadius) {
            this.name = name;
            this.origin = origin;
            this.rotation = rotation;
            this.fallbackRadius = fallbackRadius;
            this.blendRadius = Math.max(8, fallbackBlendRadius);
        }

        public void setSize(Vec3i size, BlockPos pivot) {
            if (size == null || size.getX() <= 0 || size.getZ() <= 0) {
                return;
            }

            int px = Math.max(0, Math.min(pivot.getX(), size.getX() - 1));
            int pz = Math.max(0, Math.min(pivot.getZ(), size.getZ() - 1));

            this.size = size;
            this.sizeLoaded = true;

            this.localMinX = -px;
            this.localMinZ = -pz;
            this.localMaxX = size.getX() - 1 - px;
            this.localMaxZ = size.getZ() - 1 - pz;

            this.blendRadius = calculateBlendRadius(size);
        }

        /**
         * Расстояние от мировой точки (x, z) до фактического пятна застройки структуры.
         * Если точка внутри структуры — вернёт 0.
         * Если структура ещё не загружена — используется fallback-радиус.
         */
        public double distanceToFootprint(int x, int z) {
            double dx = x - origin.getX();
            double dz = z - origin.getZ();

            if (!sizeLoaded) {
                return Math.sqrt(dx * dx + dz * dz) - fallbackRadius;
            }

            double lx;
            double lz;

            // Инвертируем поворот: переводим мировые координаты в локальные координаты структуры.
            switch (rotation) {
                case CLOCKWISE_90:
                    lx = dz;
                    lz = -dx;
                    break;

                case COUNTERCLOCKWISE_90:
                    lx = -dz;
                    lz = dx;
                    break;

                case CLOCKWISE_180:
                    lx = -dx;
                    lz = -dz;
                    break;

                default:
                    lx = dx;
                    lz = dz;
                    break;
            }

            double distX = 0.0;
            if (lx < localMinX) {
                distX = localMinX - lx;
            } else if (lx > localMaxX) {
                distX = lx - localMaxX;
            }

            double distZ = 0.0;
            if (lz < localMinZ) {
                distZ = localMinZ - lz;
            } else if (lz > localMaxZ) {
                distZ = lz - localMaxZ;
            }

            if (distX <= 0.0 && distZ <= 0.0) {
                return 0.0;
            }

            return Math.sqrt(distX * distX + distZ * distZ);
        }
    }
    public static List<GladeStructureInfo> getGladeStructureInfos() {
        return gladeStructureInfos;
    }

    public static boolean isInsideGladeStructureZone(int x, int z) {
        return isInsideGladeStructureZone(x, z, 1);
    }

    public static boolean isInsideGladeStructureZone(int x, int z, int margin) {
        for (GladeStructureInfo info : gladeStructureInfos) {
            if (info.distanceToFootprint(x, z) <= margin) {
                return true;
            }
        }

        return false;
    }

    public static boolean isTooCloseToGladeStructures(int x, int z, int radius, int minGap) {
        for (GladeStructureInfo info : gladeStructureInfos) {
            double dist = info.distanceToFootprint(x, z);

            if (dist < radius + minGap) {
                return true;
            }
        }

        return false;
    }

    public static int getGladeStructurePlacementRadius(String name, int fallback) {
        Vec3i size = gladeSizeCache.get(name);

        if (size == null || size.getX() <= 0 || size.getZ() <= 0) {
            return fallback;
        }

        BlockPos pivot = getPivotOffset(name, size);

        int minX = -pivot.getX();
        int maxX = size.getX() - 1 - pivot.getX();
        int minZ = -pivot.getZ();
        int maxZ = size.getZ() - 1 - pivot.getZ();

        double maxSq = 0.0;

        int[][] corners = {
                {minX, minZ},
                {maxX, minZ},
                {minX, maxZ},
                {maxX, maxZ}
        };

        for (int[] c : corners) {
            double sq = (double) c[0] * c[0] + (double) c[1] * c[1];
            if (sq > maxSq) {
                maxSq = sq;
            }
        }

        return Math.max(1, (int) Math.ceil(Math.sqrt(maxSq)));
    }
    private static int calculateBlendRadius(Vec3i size) {
        int horizontal = Math.max(size.getX(), size.getZ());

        // Радиус сглаживания от ГРАНИЦЫ структуры наружу.
        // Можно подстроить под твои постройки.
        return Math.max(10, 6 + horizontal / 3);
    }

    public static BlockPos getPivotOffset(String name, Vec3i size) {
        BlockPos p = gladePivotOffsets.get(name);

        if (p == null) {
            return BlockPos.ZERO;
        }

        int x = Math.max(0, Math.min(p.getX(), size.getX() - 1));
        int z = Math.max(0, Math.min(p.getZ(), size.getZ() - 1));

        return new BlockPos(x, 0, z);
    }
    public static void preloadGladeSizes() {
        if (gladeSizesLoaded) {
            return;
        }

        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

            if (server == null) {
                return;
            }

            loadGladeSizes(server.getStructureManager());
        } catch (Throwable ignored) {
            // Если сервер ещё недоступен — работаем на fallback-радиусах.
        }
    }

    public static void loadGladeSizes(WorldGenLevel level) {
        if (gladeSizesLoaded) {
            return;
        }

        try {
            loadGladeSizes(level.getLevel().getServer().getStructureManager());
        } catch (Throwable ignored) {
            // Игнорируем, если сервер/менеджер недоступен.
        }
    }

    private static void loadGladeSizes(StructureTemplateManager manager) {
        for (String name : GLADE_STRUCTURE_NAMES) {
            if (!gladeSizeCache.containsKey(name)) {
                try {
                    Optional<StructureTemplate> opt = manager.get(new ResourceLocation("labyrinthmod", name));

                    if (opt.isPresent()) {
                        gladeSizeCache.put(name, opt.get().getSize());
                    }
                } catch (Throwable ignored) {
                    // Если структура не найдена, оставляем fallback.
                }
            }
        }

        for (GladeStructureInfo info : gladeStructureInfos) {
            Vec3i size = gladeSizeCache.get(info.name);

            if (size != null && !info.sizeLoaded) {
                info.setSize(size, getPivotOffset(info.name, size));
                registerOrUpdateProtectedRegion(info);
            }
        }

        gladeSizesLoaded = true;
    }
    public static int[] getFootprintAabb(String name, BlockPos pos, Rotation rotation, int fallbackRadius, int margin) {
        Vec3i size = gladeSizeCache.get(name);

        if (size == null || size.getX() <= 0 || size.getZ() <= 0) {
            int r = fallbackRadius + margin;
            return new int[]{
                    pos.getX() - r,
                    pos.getX() + r,
                    pos.getZ() - r,
                    pos.getZ() + r
            };
        }

        BlockPos pivot = getPivotOffset(name, size);

        int minX = -pivot.getX() - margin;
        int maxX = size.getX() - 1 - pivot.getX() + margin;
        int minZ = -pivot.getZ() - margin;
        int maxZ = size.getZ() - 1 - pivot.getZ() + margin;

        return transformAabb(pos, rotation, minX, maxX, minZ, maxZ);
    }

    private static int[] transformAabb(BlockPos pos, Rotation rotation, int minX, int maxX, int minZ, int maxZ) {
        int[][] corners = {
                {minX, minZ},
                {maxX, minZ},
                {minX, maxZ},
                {maxX, maxZ}
        };

        int worldMinX = Integer.MAX_VALUE;
        int worldMaxX = Integer.MIN_VALUE;
        int worldMinZ = Integer.MAX_VALUE;
        int worldMaxZ = Integer.MIN_VALUE;

        for (int[] corner : corners) {
            int[] rotated = rotateLocal(corner[0], corner[1], rotation);

            int wx = pos.getX() + rotated[0];
            int wz = pos.getZ() + rotated[1];

            if (wx < worldMinX) worldMinX = wx;
            if (wx > worldMaxX) worldMaxX = wx;
            if (wz < worldMinZ) worldMinZ = wz;
            if (wz > worldMaxZ) worldMaxZ = wz;
        }

        return new int[]{worldMinX, worldMaxX, worldMinZ, worldMaxZ};
    }

    private static void registerOrUpdateProtectedRegion(GladeStructureInfo info) {
        protectedRegions.removeIf(r -> r.structureName.equals(info.name));

        int margin = 2;

        int[] aabb = getFootprintAabb(
                info.name,
                info.origin,
                info.rotation,
                info.fallbackRadius,
                margin
        );

        BlockPos min = new BlockPos(aabb[0], info.origin.getY(), aabb[2]);
        Vec3i size = new Vec3i(
                aabb[1] - aabb[0] + 1,
                80,
                aabb[3] - aabb[2] + 1
        );

        protectedRegions.add(new ProtectedRegion(info.name, min, size));
    }
    private static boolean isGladeStructure(String name) {
        return "ferma".equals(name)
                || "banfair".equals(name)
                || "tower".equals(name)
                || "lager".equals(name);
    }

    private static int[] rotateLocal(int x, int z, Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return new int[]{-z, x};

            case COUNTERCLOCKWISE_90:
                return new int[]{z, -x};

            case CLOCKWISE_180:
                return new int[]{-x, -z};

            default:
                return new int[]{x, z};
        }
    }

    private static int[] transformLocalBounds(
            BlockPos origin,
            Rotation rotation,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        int[][] corners = {
                {minX, minZ},
                {maxX, minZ},
                {minX, maxZ},
                {maxX, maxZ}
        };

        int worldMinX = Integer.MAX_VALUE;
        int worldMaxX = Integer.MIN_VALUE;
        int worldMinZ = Integer.MAX_VALUE;
        int worldMaxZ = Integer.MIN_VALUE;

        for (int[] corner : corners) {
            int[] rotated = rotateLocal(corner[0], corner[1], rotation);

            int wx = origin.getX() + rotated[0];
            int wz = origin.getZ() + rotated[1];

            if (wx < worldMinX) worldMinX = wx;
            if (wx > worldMaxX) worldMaxX = wx;
            if (wz < worldMinZ) worldMinZ = wz;
            if (wz > worldMaxZ) worldMaxZ = wz;
        }

        return new int[]{worldMinX, worldMaxX, worldMinZ, worldMaxZ};
    }



}