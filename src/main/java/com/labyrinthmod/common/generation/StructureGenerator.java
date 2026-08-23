package com.labyrinthmod.common.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StructureGenerator {

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
        public final boolean breakable; // ★ НОВОЕ

        public StructureData(StructurePlacement placement, BlockPos origin) {
            this.placement = placement;
            this.origin = origin;
            this.breakable = placement.isBreakable(); // ★ Берём из StructurePlacement
        }

        public boolean intersectsChunk(int chunkX, int chunkZ) {
            if (size == null) return true;
            int minX = origin.getX();
            int maxX = origin.getX() + size.getX();
            int minZ = origin.getZ();
            int maxZ = origin.getZ() + size.getZ();
            int cMinX = chunkX << 4;
            int cMaxX = cMinX + 15;
            int cMinZ = chunkZ << 4;
            int cMaxZ = cMinZ + 15;
            return cMaxX >= minX && cMinX <= maxX && cMaxZ >= minZ && cMinZ <= maxZ;
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

        // ★ Пример: если нужна ломаемая структура — просто true (или без параметра):
        // structures.add(new StructureData(
        //         new StructurePlacement("decoration", "labyrinthmod", "deco_1",
        //                 new BlockPos(50, 32, 50), true),
        //         new BlockPos(50, 32, 50)
        // ));
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
        loadSizesIfNeeded(level);
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        for (StructureData data : structures) {
            if (data.intersectsChunk(chunkX, chunkZ)) {
                data.placement.place(level, null);

                // ★ РЕГИСТРИРУЕМ ЗАЩИЩЁННУЮ ЗОНУ, если структура не ломаемая
                if (!data.breakable && data.size != null && data.size.getX() > 0) {
                    // Проверяем, не добавлена ли уже эта зона
                    boolean alreadyRegistered = protectedRegions.stream()
                            .anyMatch(r -> r.structureName.equals(data.placement.getName()));
                    if (!alreadyRegistered) {
                        protectedRegions.add(
                                new ProtectedRegion(data.placement.getName(), data.origin, data.size)
                        );
                        System.out.println("[StructureGenerator] Protected region registered: "
                                + data.placement.getName()
                                + " (" + data.origin + " -> "
                                + (data.origin.getX() + data.size.getX()) + ","
                                + (data.origin.getY() + data.size.getY()) + ","
                                + (data.origin.getZ() + data.size.getZ()) + ")");
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
}