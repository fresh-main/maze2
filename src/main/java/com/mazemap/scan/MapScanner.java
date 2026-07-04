package com.mazemap.scan;

import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.network.packet.S2CFragmentSyncPacket;
import com.mazemap.storage.PlayerMapData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;

public final class MapScanner {
    public static final int SCAN_RADIUS = 12;
    public static final int SCAN_INTERVAL_TICKS = 4;
    public static final int MAX_SCAN_Y = 300;
    private MapScanner() {}

    // ==========================================
    // ПРОВЕРКИ БЛОКОВ
    // ==========================================

    /**
     * Визуальный слой: определяет, отображается ли блок на карте.
     * Листва (LEAVES) возвращается как true, так как в ванилле она рисуется на картах.
     */
    private static boolean isRenderableBlock(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(BlockTags.REPLACEABLE) ||
                state.is(BlockTags.FLOWERS) || state.is(BlockTags.TALL_FLOWERS) ||
                state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.SAPLINGS) ||
                state.is(BlockTags.CROPS)) return false;

        // Листва имеет MapColor.PLANT, но мы хотим её рисовать!
        if (state.is(BlockTags.LEAVES)) return true;

        if (state.getMapColor(level, pos) == MapColor.PLANT || state.getMapColor(level, pos) == MapColor.NONE) return false;

        return state.isCollisionShapeFullBlock(level, pos);
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

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int scale = PlayerMapData.FRAGMENT_SIZE / PlayerMapData.FRAGMENT_SIZE_BLOCKS;

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int worldX = center.getX() + dx;
                int worldZ = center.getZ() + dz;

                // 1. СЛОЙ ОТРИСОВКИ
                int startYRender = Math.min(MAX_SCAN_Y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ));
                int surfaceYRender = Integer.MIN_VALUE;
                for (int y = startYRender; y >= level.getMinBuildHeight(); y--) {
                    cursor.set(worldX, y, worldZ);
                    if (isRenderableBlock(level.getBlockState(cursor), level, cursor)) {
                        surfaceYRender = y;
                        break;
                    }
                }

                byte color = PlayerMapData.PIXEL_UNEXPLORED;
                if (surfaceYRender != Integer.MIN_VALUE) {
                    cursor.set(worldX, surfaceYRender, worldZ);
                    BlockState surfaceBlock = level.getBlockState(cursor);
                    MapColor mapColor = surfaceBlock.getMapColor(level, cursor);
                    if (mapColor == MapColor.NONE) mapColor = MapColor.STONE;

                    int baseBrightness = 2;
                    cursor.set(worldX, surfaceYRender + 1, worldZ);
                    boolean aboveIsWall = isRenderableBlock(level.getBlockState(cursor), level, cursor);
                    cursor.set(worldX, surfaceYRender - 1, worldZ);
                    boolean belowIsWall = isRenderableBlock(level.getBlockState(cursor), level, cursor);

                    if (aboveIsWall) baseBrightness = 1;
                    else if (!belowIsWall) baseBrightness = 3;

                    int heightFactor = (surfaceYRender - 64) / 16;
                    int finalBrightness = Math.max(0, Math.min(3, baseBrightness + heightFactor));
                    int packed = (mapColor.id << 2) | finalBrightness;
                    color = (byte) (2 + packed);
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
                            new S2CFragmentSyncPacket(cellX, cellZ, frag.pixels, frag.walkable));
                }
            }
        }
    }
}