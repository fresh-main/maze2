package com.mazemap.client;

import com.mazemap.storage.PlayerMapData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class MazePathFinder {
    public static final int MAX_BFS_RADIUS = 200;
    public static final int MAX_BFS_VISITED = 80_000;
    private static final int[][] DIRS_4 = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private MazePathFinder() {}

    public static List<int[]> findPath(int startX, int startZ, int endX, int endZ) {
        Map<Long, PlayerMapData.Fragment> frags = ClientMazeMapHandlers.getFragments();
        if (!isPassage(frags, endX, endZ)) return Collections.emptyList();
        if (!isPassage(frags, startX, startZ)) return Collections.emptyList();

        ArrayDeque<int[]> queue = new ArrayDeque<>();
        Map<Long, Long> parent = new HashMap<>();
        long startKey = pack(startX, startZ);
        long endKey = pack(endX, endZ);
        long noParent = Long.MIN_VALUE;
        queue.add(new int[]{startX, startZ});
        parent.put(startKey, noParent);

        boolean found = false;
        while (!queue.isEmpty() && parent.size() < MAX_BFS_VISITED) {
            int[] p = queue.poll();
            if (p[0] == endX && p[1] == endZ) {
                found = true;
                break;
            }
            for (int[] d : DIRS_4) {
                int nx = p[0] + d[0];
                int nz = p[1] + d[1];
                if (Math.abs(nx - startX) > MAX_BFS_RADIUS) continue;
                if (Math.abs(nz - startZ) > MAX_BFS_RADIUS) continue;
                long nkey = pack(nx, nz);
                if (parent.containsKey(nkey)) continue;
                if (!isPassage(frags, nx, nz)) continue;
                parent.put(nkey, pack(p[0], p[1]));
                queue.add(new int[]{nx, nz});
            }
        }

        if (!found) return Collections.emptyList();

        List<int[]> path = new ArrayList<>();
        long cur = endKey;
        while (cur != noParent) {
            path.add(unpack(cur));
            Long pp = parent.get(cur);
            if (pp == null || pp == noParent) break;
            cur = pp;
        }
        Collections.reverse(path);
        return path;
    }

    // ИСПРАВЛЕНО: Теперь принимаем Map<Long, Fragment>
    public static boolean isPassage(Map<Long, PlayerMapData.Fragment> frags, int worldX, int worldZ) {
        int cellX = Math.floorDiv(worldX, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
        int cellZ = Math.floorDiv(worldZ, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
        long key = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);

        PlayerMapData.Fragment frag = frags.get(key);
        if (frag == null) return false; // Не исследовано = нельзя идти

        int localX = Math.floorMod(worldX, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
        int localZ = Math.floorMod(worldZ, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
        int scale = PlayerMapData.FRAGMENT_SIZE / PlayerMapData.FRAGMENT_SIZE_BLOCKS;
        int pixelX = localX * scale;
        int pixelZ = localZ * scale;

        // ГЛАВНОЕ: Проверяем массив проходимости (walkable), а не цвета!
        return frag.walkable[pixelZ * PlayerMapData.FRAGMENT_SIZE + pixelX] != 0;
    }

    public static boolean isExplored(int worldX, int worldZ) {
        Map<Long, PlayerMapData.Fragment> frags = ClientMazeMapHandlers.getFragments();
        int cellX = Math.floorDiv(worldX, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
        int cellZ = Math.floorDiv(worldZ, PlayerMapData.FRAGMENT_SIZE_BLOCKS);
        long key = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        return frags.get(key) != null;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int[] unpack(long key) {
        return new int[]{(int) (key >> 32), (int) key};
    }
}