package com.mazemap.scan;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.network.packet.S2CFragmentSyncPacket;
import com.mazemap.storage.PlayerMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class MapScanner {
    /** Захватывает крупную крону целиком, даже если игрок стоит около ствола. */
    public static final int SCAN_RADIUS = 32;
    /** Большой визуальный радиус сканируется раз в секунду, чтобы не нагружать сервер. */
    public static final int SCAN_INTERVAL_TICKS = 20;
    public static final int MAX_SCAN_Y = 300;
    private MapScanner() {}

    private record MovingSurface(int y, BlockState state) {}

    // ==========================================
    // ПРОВЕРКИ БЛОКОВ
    // ==========================================

    /**
     * Визуальный слой: определяет, отображается ли блок на карте.
     * Листва (LEAVES) возвращается как true, так как в ванилле она рисуется на картах.
     */
    private static boolean isRenderableBlock(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.getFluidState().is(FluidTags.WATER)) return true;
        if (isTreeFoliage(state)) return true;
        // Механизмы Create часто имеют MapColor.NONE и неполную форму коллизии,
        // поэтому обычная проверка полной грани скрывала их с карты.
        if (isCreateBlock(state)) return !state.getShape(level, pos).isEmpty();
        if (state.is(BlockTags.REPLACEABLE) ||
                state.is(BlockTags.FLOWERS) || state.is(BlockTags.TALL_FLOWERS) ||
                state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.SAPLINGS) ||
                state.is(BlockTags.CROPS)) return false;

        if (state.getMapColor(level, pos) == MapColor.PLANT || state.getMapColor(level, pos) == MapColor.NONE) return false;

        return state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isCreateBlock(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && "create".equals(id.getNamespace());
    }

    private static boolean isTreeFoliage(BlockState state) {
        if (state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock) return true;
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("leaves") || path.endsWith("_leaf") || path.startsWith("leaf_");
    }

    private static byte treePixel(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        String path = id == null ? "" : id.getPath();
        if (path.contains("spruce") || path.contains("pine") || path.contains("fir")
                || path.contains("cedar") || path.contains("redwood")) {
            return PlayerMapData.PIXEL_TREE_CONIFER;
        }
        if (path.contains("birch") || path.contains("aspen")) {
            return PlayerMapData.PIXEL_TREE_BIRCH;
        }
        return PlayerMapData.PIXEL_TREE;
    }

    private static boolean isStructureBlock(BlockState state) {
        var id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("brick") || path.contains("cobble") || path.contains("masonry")
                || path.contains("plank") || path.contains("concrete")
                || path.contains("terracotta") || path.contains("glass") || path.contains("tile")
                || path.contains("wall") || path.contains("fence") || path.contains("door")
                || path.contains("stairs") || path.contains("slab") || path.contains("pillar")
                || path.contains("beam") || path.contains("metal") || path.contains("iron")
                || path.contains("copper") || path.contains("foundation");
    }

    /**
     * Физический слой: определяет, является ли блок твёрдым полом для поиска пути.
     * Листва и растения игнорируются, так как через них можно ходить.
     */
    private static boolean isSolidForPathfinding(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE) ||
                state.is(BlockTags.FLOWERS) || state.is(BlockTags.TALL_FLOWERS) ||
                state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.SAPLINGS) ||
                state.is(BlockTags.CROPS)) return false;
        if (state.getMapColor(level, pos) == MapColor.PLANT) return false;

        return state.isCollisionShapeFullBlock(level, pos);
    }

    public static void scan(ServerPlayer player) {
        if (player.tickCount % SCAN_INTERVAL_TICKS != 0) return;

        ItemStack mapStack = PersonalMapItem.findInInventory(player);
        if (mapStack.isEmpty()) return;

        // 🧊 ПРОВЕРКА ПРОЧНОСТИ: Если карта сломана, сканирование полностью останавливается
        if (com.mazemap.util.MapDurabilityHandler.isBroken(mapStack)) return;

        ServerLevel level = player.serverLevel();
        PlayerMapData data = PersonalMapItem.getData(mapStack);

        BlockPos center = player.blockPosition();
        Set<Long> changedFragments = new HashSet<>();
        int playerHeadY = (int) Math.floor(player.getEyeY());
        Map<Long, MovingSurface> movingSurfaces = collectMovingSurfaces(level, center);

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scale = PlayerMapData.FRAGMENT_SIZE / PlayerMapData.FRAGMENT_SIZE_BLOCKS;

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int worldX = center.getX() + dx;
                int worldZ = center.getZ() + dz;

                // 1. СЛОЙ ОТРИСОВКИ
                // Визуальный слой должен начинаться над кроной. Вариант NO_LEAVES
                // начинал поиск под листвой, из-за чего дерево распадалось на землю,
                // ствол и случайные куски кроны.
                int startYRender = Math.min(MAX_SCAN_Y,
                        level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ));
                int surfaceYRender = Integer.MIN_VALUE;
                for (int y = startYRender; y >= level.getMinBuildHeight(); y--) {
                    cursor.set(worldX, y, worldZ);
                    if (isRenderableBlock(level.getBlockState(cursor), level, cursor)) {
                        surfaceYRender = y;
                        break;
                    }
                }

                MovingSurface movingSurface = movingSurfaces.get(columnKey(worldX, worldZ));
                if (movingSurface != null && movingSurface.y() >= surfaceYRender) {
                    surfaceYRender = movingSurface.y();
                }

                byte color = PlayerMapData.PIXEL_UNEXPLORED;
                byte heightByte = 0;
                if (surfaceYRender != Integer.MIN_VALUE) {
                    heightByte = (byte) Math.max(0, Math.min(255, surfaceYRender));
                    cursor.set(worldX, surfaceYRender, worldZ);
                    BlockState surfaceBlock = movingSurface != null && movingSurface.y() == surfaceYRender
                            ? movingSurface.state() : level.getBlockState(cursor);
                    MapColor mapColor = surfaceBlock.getMapColor(level, cursor);
                    if (mapColor == MapColor.NONE) {
                        mapColor = isCreateBlock(surfaceBlock) ? MapColor.METAL : MapColor.STONE;
                    }

                    int baseBrightness = 2;
                    cursor.set(worldX, surfaceYRender + 1, worldZ);
                    boolean aboveIsWall = isRenderableBlock(level.getBlockState(cursor), level, cursor);
                    cursor.set(worldX, surfaceYRender - 1, worldZ);
                    boolean belowIsWall = isRenderableBlock(level.getBlockState(cursor), level, cursor);

                    if (aboveIsWall) baseBrightness = 1;
                    else if (!belowIsWall) baseBrightness = 3;

                    int heightFactor = (surfaceYRender - 64) / 16;
                    int finalBrightness = Math.max(0, Math.min(3, baseBrightness + heightFactor));
                    if (isTreeFoliage(surfaceBlock)) {
                        color = treePixel(surfaceBlock);
                    } else if (surfaceBlock.getFluidState().is(FluidTags.WATER)) {
                        color = PlayerMapData.PIXEL_WATER;
                    } else if (isCreateBlock(surfaceBlock)) {
                        color = PlayerMapData.PIXEL_CREATE;
                    } else if (isStructureBlock(surfaceBlock)) {
                        color = PlayerMapData.PIXEL_STRUCTURE;
                    } else {
                        int packed = (mapColor.id << 2) | finalBrightness;
                        color = (byte) (2 + packed);
                    }
                }

                // 2. СЛОЙ ПРОХОДИМОСТИ
                boolean isWalkable = false;
                int startYPath = playerHeadY + 2;
                int surfaceYPath = Integer.MIN_VALUE;
                for (int y = startYPath; y >= level.getMinBuildHeight(); y--) {
                    cursor.set(worldX, y, worldZ);
                    if (isSolidForPathfinding(level.getBlockState(cursor), level, cursor)) {
                        surfaceYPath = y;
                        break;
                    }
                }
                if (surfaceYPath != Integer.MIN_VALUE) {
                    cursor.set(worldX, surfaceYPath + 1, worldZ);
                    boolean space1 = !isSolidForPathfinding(level.getBlockState(cursor), level, cursor);
                    cursor.set(worldX, surfaceYPath + 2, worldZ);
                    boolean space2 = !isSolidForPathfinding(level.getBlockState(cursor), level, cursor);
                    isWalkable = space1 && space2;
                }
                if (movingSurface != null) isWalkable = false;

                // 3. ЗАПИСЬ В FRAGMENT
                int cellX = Math.floorDiv(worldX, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
                int cellZ = Math.floorDiv(worldZ, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
                int localX = Math.floorMod(worldX, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
                int localZ = Math.floorMod(worldZ, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
                int pixelX = localX * scale;
                int pixelZ = localZ * scale;

                PlayerMapData.Fragment fragment = data.getOrCreateFragment(cellX, cellZ);
                boolean fragChanged = false;
                byte walkByte = (byte) (isWalkable ? 1 : 0);

                for (int sx = 0; sx < scale; sx++) {
                    for (int sz = 0; sz < scale; sz++) {
                        int idx = (pixelZ + sz) * PlayerMapData.FRAGMENT_SIZE + (pixelX + sx);
                        if (fragment.pixels[idx] != color) {
                            fragment.pixels[idx] = color;
                            fragChanged = true;
                        }
                        if (fragment.walkable[idx] != walkByte) {
                            fragment.walkable[idx] = walkByte;
                            fragChanged = true;
                        }
                        if (fragment.heights[idx] != heightByte) {
                            fragment.heights[idx] = heightByte;
                            fragChanged = true;
                        }
                    }
                }

                long fragKey = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);

                // 💎 Списание прочности: 1 единица за каждый измененный фрагмент
                if (fragChanged && !changedFragments.contains(fragKey)) {
                    if (!com.mazemap.util.MapDurabilityHandler.consumeDurability(mapStack)) {
                        return; // Прочность = 0. Карта замораживается, сканирование прерывается.
                    }
                }

                if (fragChanged) {
                    data.markDirty();
                    changedFragments.add(fragKey);
                }
            }
        }

        if (data.isDirty()) {
            PersonalMapItem.setData(mapStack, data);

            for (long key : changedFragments) {
                int cellX = (int) (key >> 32);
                int cellZ = (int) key;
                PlayerMapData.Fragment frag = data.getFragment(cellX, cellZ);
                if (frag != null) {
                    MazeMapNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new S2CFragmentSyncPacket(cellX, cellZ, frag.pixels, frag.walkable, frag.heights));
                }
            }
        }
    }


    private static Map<Long, MovingSurface> collectMovingSurfaces(ServerLevel level, BlockPos center) {
        Map<Long, MovingSurface> result = new HashMap<>();
        double radius = SCAN_RADIUS + 8.0D;
        AABB search = new AABB(center).inflate(radius, MAX_SCAN_Y, radius);
        var contraptions = level.getEntitiesOfClass(AbstractContraptionEntity.class, search,
                entity -> entity.isAlive() && entity.getContraption() != null);

        for (AbstractContraptionEntity entity : contraptions) {
            for (var entry : entity.getContraption().getBlocks().entrySet()) {
                BlockState state = entry.getValue().state();
                if (state.isAir() || state.getShape(level, BlockPos.ZERO).isEmpty()) continue;

                Vec3 worldCenter = entity.toGlobalVector(Vec3.atCenterOf(entry.getKey()), 1.0F);
                int minX = (int) Math.floor(worldCenter.x - 0.499D);
                int maxX = (int) Math.floor(worldCenter.x + 0.499D);
                int minY = (int) Math.floor(worldCenter.y - 0.499D);
                int maxY = (int) Math.floor(worldCenter.y + 0.499D);
                int minZ = (int) Math.floor(worldCenter.z - 0.499D);
                int maxZ = (int) Math.floor(worldCenter.z + 0.499D);

                for (int x = minX; x <= maxX; x++) {
                    if (Math.abs(x - center.getX()) > SCAN_RADIUS) continue;
                    for (int z = minZ; z <= maxZ; z++) {
                        if (Math.abs(z - center.getZ()) > SCAN_RADIUS) continue;
                        long key = columnKey(x, z);
                        MovingSurface previous = result.get(key);
                        if (previous == null || maxY > previous.y()) {
                            result.put(key, new MovingSurface(Math.max(minY, maxY), state));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
