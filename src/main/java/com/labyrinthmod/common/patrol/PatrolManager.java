package com.labyrinthmod.common.patrol;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PatrolManager extends SavedData {
    private static final String DATA_NAME = "labyrinth_patrol";

    // Глобальный пул точек
    private final List<BlockPos> patrolPoints = new ArrayList<>();

    // Точка спавна (откуда выходят гриверы при старте)
    private BlockPos spawnPoint = null;

    // Настройки
    private int minDistanceBetweenGrivers = 50;
    private int maxTargetDistance = 150;
    private int revisitCooldown = 5; // сколько последних точек нельзя брать
    private long emergenceTime = 13000L; // ночь
    private boolean timeBasedEmergenceEnabled = false;
    private boolean globalPatrolActive = false;
    private boolean globalForceChunkLoading = true; // Принудительная загрузка чанков для всех гриверов

    // История посещённых точек на гривера (UUID -> list of recent points)
    private final Map<UUID, LinkedList<BlockPos>> griverVisitHistory = new HashMap<>();

    // Назначенные цели гриверов (UUID -> BlockPos) - чтобы не шли в одну точку
    private final Map<UUID, BlockPos> griverTargets = new HashMap<>();

    // Фаза рассредоточения (UUID -> dispersal target) - при старте патруля
    private final Map<UUID, BlockPos> dispersalTargets = new HashMap<>();

    // Зарезервированные waypoints цепочки (UUID -> все patrol points на пути этого гривера)
    private final Map<UUID, Set<BlockPos>> reservedWaypoints = new HashMap<>();

    // Очередь из N запланированных целей на гривера
    private final Map<UUID, List<BlockPos>> plannedTargets = new HashMap<>();
    public static final int PLAN_LENGTH = 5;

    // Зона патрулирования каждого гривера (кластер patrol points)
    private final Map<UUID, Set<BlockPos>> griverZones = new HashMap<>();

    // Центроиды зон — нужны для пространственного зонирования (проверка клеток в pathfinding)
    private final Map<UUID, BlockPos> griverCentroids = new HashMap<>();
    private final List<BlockPos> allCentroids = new ArrayList<>();
    // Прямоугольные границы каждой зоны: [minX, maxX, minZ, maxZ]
    private final Map<UUID, int[]> griverCellBounds = new HashMap<>();

    // Границы лабиринта (bbox) — внутри них генерируются точки
    private BlockPos boundsMin = null;
    private BlockPos boundsMax = null;

    // Зоны исключения (AABB) — внутри них точки НЕ генерируются
    public static class ExclusionZone {
        public final BlockPos a;
        public final BlockPos b;
        public final int minX, minY, minZ, maxX, maxY, maxZ;

        public ExclusionZone(BlockPos a, BlockPos b) {
            this.a = a; this.b = b;
            this.minX = Math.min(a.getX(), b.getX());
            this.minY = Math.min(a.getY(), b.getY());
            this.minZ = Math.min(a.getZ(), b.getZ());
            this.maxX = Math.max(a.getX(), b.getX());
            this.maxY = Math.max(a.getY(), b.getY());
            this.maxZ = Math.max(a.getZ(), b.getZ());
        }

        public boolean contains(BlockPos p) {
            // Зона работает как вертикальная колонна — Y игнорируется
            return p.getX() >= minX && p.getX() <= maxX
                    && p.getZ() >= minZ && p.getZ() <= maxZ;
        }

        public CompoundTag serialize() {
            CompoundTag t = new CompoundTag();
            t.putInt("ax", a.getX()); t.putInt("ay", a.getY()); t.putInt("az", a.getZ());
            t.putInt("bx", b.getX()); t.putInt("by", b.getY()); t.putInt("bz", b.getZ());
            return t;
        }

        public static ExclusionZone deserialize(CompoundTag t) {
            return new ExclusionZone(
                    new BlockPos(t.getInt("ax"), t.getInt("ay"), t.getInt("az")),
                    new BlockPos(t.getInt("bx"), t.getInt("by"), t.getInt("bz")));
        }
    }

    private final List<ExclusionZone> exclusionZones = new ArrayList<>();

    // Граф соседей между patrol points для waypoint chain
    private Map<BlockPos, List<BlockPos>> pointGraph = null;
    private static final double GRAPH_EDGE_DIST = 40.0;
    private static final double GRAPH_EDGE_DIST_SQ = GRAPH_EDGE_DIST * GRAPH_EDGE_DIST;

    public static PatrolManager get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(PatrolManager::load, PatrolManager::new, DATA_NAME);
    }

    // ========== КЭШ КАРТЫ ДЛЯ АДМИН-МЕНЮ ==========

    /** Снапшот walkable-ячеек лабиринта. Стоит дорого — 600×600×11 getBlockState на свежее построение. */
    public static class MapCache {
        public final int width;
        public final int height;
        public final int floorY;
        public final byte[] data;
        public final BlockPos cachedBoundsMin;
        public final BlockPos cachedBoundsMax;

        public MapCache(int w, int h, int fy, byte[] d, BlockPos bMin, BlockPos bMax) {
            this.width = w; this.height = h; this.floorY = fy; this.data = d;
            this.cachedBoundsMin = bMin; this.cachedBoundsMax = bMax;
        }
    }

    private MapCache mapCache = null;
    private static final int MAP_MAX_SIDE = 600;

    /** Сбрасываем кэш карты при ЛЮБОЙ смене границ — иначе картинка устаревает. */
    private void invalidateMapCache() {
        mapCache = null;
    }

    /**
     * Возвращает кэшированную карту или строит новую (heavy: до миллионов getBlockState).
     * Кэш сбрасывается только при смене bounds — рельеф внутри границ обычно не
     * меняется во время игры, и редкие изменения переживём 1 рестартом сервера.
     */
    public MapCache getOrBuildMapCache(Level level) {
        if (boundsMin == null || boundsMax == null) return null;
        if (mapCache != null
                && boundsMin.equals(mapCache.cachedBoundsMin)
                && boundsMax.equals(mapCache.cachedBoundsMax)) {
            return mapCache;
        }

        int minX = Math.min(boundsMin.getX(), boundsMax.getX());
        int maxX = Math.max(boundsMin.getX(), boundsMax.getX());
        int minZ = Math.min(boundsMin.getZ(), boundsMax.getZ());
        int maxZ = Math.max(boundsMin.getZ(), boundsMax.getZ());
        int fy = Math.min(boundsMin.getY(), boundsMax.getY());

        int stepX = Math.max(1, (maxX - minX + 1) / MAP_MAX_SIDE);
        int stepZ = Math.max(1, (maxZ - minZ + 1) / MAP_MAX_SIDE);
        int mw = (maxX - minX + 1 + stepX - 1) / stepX;
        int mh = (maxZ - minZ + 1 + stepZ - 1) / stepZ;

        byte[] data = new byte[mw * mh];
        for (int i = 0; i < mw; i++) {
            for (int j = 0; j < mh; j++) {
                int x = minX + i * stepX;
                int z = minZ + j * stepZ;
                boolean walkable = false;
                for (int dy = -5; dy <= 5; dy++) {
                    BlockPos p = new BlockPos(x, fy + dy, z);
                    var below = level.getBlockState(p.below());
                    if (below.isAir() || !below.isSolid()) continue;
                    if (!level.getBlockState(p).isAir()) continue;
                    if (!level.getBlockState(p.above()).isAir()) continue;
                    walkable = true; break;
                }
                data[i * mh + j] = (byte) (walkable ? 1 : 0);
            }
        }

        mapCache = new MapCache(mw, mh, fy, data, boundsMin, boundsMax);
        setDirty(); // персистим — после рестарта сервера первый заход в админку без лага
        return mapCache;
    }

    // ========== ТОЧКИ ==========

    public void addPatrolPoint(BlockPos pos) {
        if (!patrolPoints.contains(pos)) {
            patrolPoints.add(pos);
            pointGraph = null;
            setDirty();
        }
    }

    public boolean removePatrolPoint(BlockPos pos) {
        boolean removed = patrolPoints.remove(pos);
        if (removed) {
            pointGraph = null;
            setDirty();
        }
        return removed;
    }

    public void togglePatrolPoint(BlockPos pos) {
        if (!patrolPoints.remove(pos)) {
            patrolPoints.add(pos);
        }
        pointGraph = null;
        setDirty();
    }

    public void clearPatrolPoints() {
        patrolPoints.clear();
        pointGraph = null;
        setDirty();
    }

    public List<BlockPos> getPatrolPoints() {
        return new ArrayList<>(patrolPoints);
    }

    public boolean hasPoint(BlockPos pos) {
        return patrolPoints.contains(pos);
    }

    // ========== ТОЧКА СПАВНА ==========

    public void setSpawnPoint(BlockPos pos) {
        this.spawnPoint = pos;
        setDirty();
    }

    public BlockPos getSpawnPoint() {
        return spawnPoint;
    }

    // ========== НАСТРОЙКИ ==========

    public int getMinDistanceBetweenGrivers() {
        return minDistanceBetweenGrivers;
    }

    public void setMinDistanceBetweenGrivers(int value) {
        this.minDistanceBetweenGrivers = Math.max(5, value);
        setDirty();
    }

    public int getMaxTargetDistance() { return maxTargetDistance; }
    public void setMaxTargetDistance(int value) {
        this.maxTargetDistance = Math.max(20, value);
        setDirty();
    }

    public int getRevisitCooldown() {
        return revisitCooldown;
    }

    public void setRevisitCooldown(int value) {
        this.revisitCooldown = Math.max(0, value);
        setDirty();
    }

    public long getEmergenceTime() {
        return emergenceTime;
    }

    public void setEmergenceTime(long time) {
        this.emergenceTime = ((time % 24000L) + 24000L) % 24000L;
        setDirty();
    }

    public boolean isTimeBasedEmergenceEnabled() {
        return timeBasedEmergenceEnabled;
    }

    public void setTimeBasedEmergenceEnabled(boolean enabled) {
        this.timeBasedEmergenceEnabled = enabled;
        setDirty();
    }

    public boolean isGlobalPatrolActive() {
        return globalPatrolActive;
    }

    public void setGlobalPatrolActive(boolean active) {
        this.globalPatrolActive = active;
        if (!active) {
            griverTargets.clear();
            dispersalTargets.clear();
        }
        setDirty();
    }

    // ========== ПРИНУДИТЕЛЬНАЯ ЗАГРУЗКА ЧАНКОВ ==========

    public boolean isGlobalForceChunkLoading() {
        return globalForceChunkLoading;
    }

    public void setGlobalForceChunkLoading(boolean enabled) {
        this.globalForceChunkLoading = enabled;
        setDirty();
    }

    // ========== НАЗНАЧЕНИЕ ЦЕЛЕЙ ==========

    public BlockPos getGriverTarget(UUID griverId) {
        return griverTargets.get(griverId);
    }

    public void setGriverTarget(UUID griverId, BlockPos target) {
        if (target == null) {
            griverTargets.remove(griverId);
        } else {
            griverTargets.put(griverId, target);
        }
        setDirty();
    }

    public void clearGriverTarget(UUID griverId) {
        griverTargets.remove(griverId);
        setDirty();
    }

    public Map<UUID, BlockPos> getAllGriverTargets() {
        return new HashMap<>(griverTargets);
    }

    // ========== ИСТОРИЯ ПОСЕЩЕНИЙ ==========

    public void recordVisit(UUID griverId, BlockPos pos) {
        LinkedList<BlockPos> history = griverVisitHistory.computeIfAbsent(griverId, k -> new LinkedList<>());
        history.addLast(pos);
        while (history.size() > revisitCooldown) {
            history.removeFirst();
        }
        setDirty();
    }

    public List<BlockPos> getRecentVisits(UUID griverId) {
        return new ArrayList<>(griverVisitHistory.getOrDefault(griverId, new LinkedList<>()));
    }

    public void clearVisitHistory(UUID griverId) {
        griverVisitHistory.remove(griverId);
        setDirty();
    }

    // ========== DISPERSAL ==========

    public void setDispersalTarget(UUID griverId, BlockPos target) {
        if (target == null) dispersalTargets.remove(griverId);
        else dispersalTargets.put(griverId, target);
        setDirty();
    }

    public BlockPos getDispersalTarget(UUID griverId) {
        return dispersalTargets.get(griverId);
    }

    public void clearDispersalTarget(UUID griverId) {
        dispersalTargets.remove(griverId);
        setDirty();
    }

    public boolean hasDispersalTarget(UUID griverId) {
        return dispersalTargets.containsKey(griverId);
    }

    // ========== RESERVED WAYPOINTS ==========

    public void setReservedWaypoints(UUID griverId, List<BlockPos> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            reservedWaypoints.remove(griverId);
        } else {
            reservedWaypoints.put(griverId, new HashSet<>(waypoints));
        }
    }

    public void clearReservedWaypoints(UUID griverId) {
        reservedWaypoints.remove(griverId);
    }

    public Set<BlockPos> getAllReservedWaypointsExcept(UUID griverId) {
        Set<BlockPos> all = new HashSet<>();
        for (Map.Entry<UUID, Set<BlockPos>> e : reservedWaypoints.entrySet()) {
            if (!e.getKey().equals(griverId)) all.addAll(e.getValue());
        }
        return all;
    }

    // ========== PLANNED TARGETS (очередь на 5 точек) ==========

    public List<BlockPos> getPlannedTargets(UUID griverId) {
        return new ArrayList<>(plannedTargets.getOrDefault(griverId, Collections.emptyList()));
    }

    public void setPlannedTargets(UUID griverId, List<BlockPos> list) {
        if (list == null || list.isEmpty()) plannedTargets.remove(griverId);
        else plannedTargets.put(griverId, new ArrayList<>(list));
    }

    public void clearPlannedTargets(UUID griverId) {
        plannedTargets.remove(griverId);
    }

    // ========== ЗОНЫ ПАТРУЛИРОВАНИЯ ==========

    public Set<BlockPos> getGriverZone(UUID griverId) {
        return griverZones.getOrDefault(griverId, Collections.emptySet());
    }

    public void setGriverZone(UUID griverId, Set<BlockPos> zone) {
        if (zone == null || zone.isEmpty()) griverZones.remove(griverId);
        else griverZones.put(griverId, new HashSet<>(zone));
    }

    public void clearGriverZone(UUID griverId) {
        griverZones.remove(griverId);
        griverCentroids.remove(griverId);
        griverCellBounds.remove(griverId);
    }

    public void clearAllZones() {
        griverZones.clear();
        griverCentroids.clear();
        allCentroids.clear();
        griverCellBounds.clear();
    }

    public BlockPos getGriverCentroid(UUID id) {
        return griverCentroids.get(id);
    }

    public int[] getGriverCellBounds(UUID id) {
        return griverCellBounds.get(id);
    }

    public List<BlockPos> getAllCentroids() {
        return new ArrayList<>(allCentroids);
    }

    /**
     * Проверяет владеет ли гривер этой клеткой: клетка должна быть в его прямоугольной зоне.
     */
    public boolean ownsCell(UUID griverId, BlockPos cell) {
        int[] b = griverCellBounds.get(griverId);
        if (b == null) return true;
        return cell.getX() >= b[0] && cell.getX() <= b[1]
                && cell.getZ() >= b[2] && cell.getZ() <= b[3];
    }

    /**
     * Делит все patrol points на зоны (по одному кластеру на гривера) через k-means.
     * Центроидами берутся позиции гриверов.
     */
    public Map<UUID, Set<BlockPos>> computeZones(List<UUID> griverIds, Map<UUID, BlockPos> positions) {
        Map<UUID, Set<BlockPos>> zones = new HashMap<>();
        if (griverIds.isEmpty() || patrolPoints.isEmpty()) return zones;

        // Начальные центроиды — позиции гриверов
        Map<UUID, double[]> centroids = new HashMap<>();
        for (UUID id : griverIds) {
            BlockPos p = positions.get(id);
            centroids.put(id, new double[]{p.getX(), p.getZ()});
            zones.put(id, new HashSet<>());
        }

        // k-means: 5 итераций достаточно
        for (int iter = 0; iter < 8; iter++) {
            for (Set<BlockPos> z : zones.values()) z.clear();

            // Присваиваем каждую точку ближайшему центроиду
            for (BlockPos p : patrolPoints) {
                UUID bestId = null;
                double bestDsq = Double.MAX_VALUE;
                for (UUID id : griverIds) {
                    double[] c = centroids.get(id);
                    double dx = p.getX() - c[0];
                    double dz = p.getZ() - c[1];
                    double d = dx * dx + dz * dz;
                    if (d < bestDsq) {
                        bestDsq = d;
                        bestId = id;
                    }
                }
                if (bestId != null) zones.get(bestId).add(p);
            }

            // Пересчитываем центроиды как среднее
            boolean changed = false;
            for (UUID id : griverIds) {
                Set<BlockPos> z = zones.get(id);
                if (z.isEmpty()) continue;
                double sx = 0, sz = 0;
                for (BlockPos p : z) { sx += p.getX(); sz += p.getZ(); }
                double cx = sx / z.size();
                double cz = sz / z.size();
                double[] old = centroids.get(id);
                if (Math.abs(old[0] - cx) > 0.5 || Math.abs(old[1] - cz) > 0.5) changed = true;
                centroids.put(id, new double[]{cx, cz});
            }
            if (!changed) break;
        }

        return zones;
    }

    public Set<BlockPos> getAllPlannedExcept(UUID griverId) {
        Set<BlockPos> all = new HashSet<>();
        for (Map.Entry<UUID, List<BlockPos>> e : plannedTargets.entrySet()) {
            if (!e.getKey().equals(griverId)) all.addAll(e.getValue());
        }
        return all;
    }

    /**
     * Глобальное планирование для всех гриверов сразу.
     * Распределяет все patrol points между гриверами так, чтобы они были
     * максимально удалены друг от друга. Round-robin с жадным выбором.
     */
    public Map<UUID, List<BlockPos>> planAllGriversGlobally(
            List<UUID> griverIds, Map<UUID, BlockPos> griverPositions, int planLength) {

        Map<UUID, List<BlockPos>> plans = new HashMap<>();
        for (UUID id : griverIds) plans.put(id, new ArrayList<>());

        int n = griverIds.size();
        if (n == 0 || patrolPoints.isEmpty()) return plans;

        // СЕТОЧНОЕ РАЗБИЕНИЕ: делим bounds на N прямоугольных зон фиксированной сетки.
        // Это гарантирует что зоны никогда не пересекаются физически.
        int minX, maxX, minZ, maxZ;
        if (hasBounds()) {
            minX = Math.min(boundsMin.getX(), boundsMax.getX());
            maxX = Math.max(boundsMin.getX(), boundsMax.getX());
            minZ = Math.min(boundsMin.getZ(), boundsMax.getZ());
            maxZ = Math.max(boundsMin.getZ(), boundsMax.getZ());
        } else {
            // Берём bounding box всех patrol points
            minX = Integer.MAX_VALUE; maxX = Integer.MIN_VALUE;
            minZ = Integer.MAX_VALUE; maxZ = Integer.MIN_VALUE;
            for (BlockPos p : patrolPoints) {
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            }
        }

        // Оптимальная сетка: выбираем (cols, rows) с минимальным количеством пустых ячеек
        // и соотношением сторон ячейки близким к 1:1 (учитывая пропорции лабиринта).
        int boundsW = maxX - minX + 1;
        int boundsH = maxZ - minZ + 1;
        int cols = 1, rows = n;
        double bestScore = Double.MAX_VALUE;
        for (int c = 1; c <= n; c++) {
            int r = (int) Math.ceil((double) n / c);
            int empty = c * r - n;
            if (empty > 1) continue; // допускаем только 1 пустую, иначе слишком неравномерно
            double cw = (double) boundsW / c;
            double ch = (double) boundsH / r;
            double aspect = Math.max(cw / ch, ch / cw);
            // Предпочитаем меньшее соотношение сторон + меньше пустых
            double score = aspect + empty * 0.5;
            if (score < bestScore) {
                bestScore = score;
                cols = c;
                rows = r;
            }
        }
        double cellW = (double) boundsW / cols;
        double cellH = (double) boundsH / rows;

        // Создаём N зон с центроидами в центре каждой ячейки + буфер
        // Гриверы работают только в СЖАТОЙ части ячейки — между зонами остаётся
        // нейтральная полоса шириной = minDistance (гарантия расстояния между гриверами)
        int margin = Math.max(3, minDistanceBetweenGrivers / 2);

        List<BlockPos> centroids = new ArrayList<>();
        List<int[]> cellBounds = new ArrayList<>(); // [minX, maxX, minZ, maxZ] каждой СЖАТОЙ ячейки
        int cellIdx = 0;
        outer:
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cellIdx >= n) break outer;
                // Полные границы ячейки
                int fullCx1 = (int) Math.floor(minX + c * cellW);
                int fullCx2 = (int) Math.floor(minX + (c + 1) * cellW - 1);
                int fullCz1 = (int) Math.floor(minZ + r * cellH);
                int fullCz2 = (int) Math.floor(minZ + (r + 1) * cellH - 1);
                // Сжатые границы с margin внутрь
                int cx1 = fullCx1 + (c > 0 ? margin : 0);
                int cx2 = fullCx2 - (c < cols - 1 ? margin : 0);
                int cz1 = fullCz1 + (r > 0 ? margin : 0);
                int cz2 = fullCz2 - (r < rows - 1 ? margin : 0);
                // Если margin съел всю ячейку — возвращаемся к полным границам
                if (cx1 >= cx2 || cz1 >= cz2) {
                    cx1 = fullCx1; cx2 = fullCx2; cz1 = fullCz1; cz2 = fullCz2;
                }
                int ccx = (cx1 + cx2) / 2;
                int ccz = (cz1 + cz2) / 2;
                // Центроид = ближайшая patrol point к центру ячейки
                BlockPos bestCentroid = null;
                double bestDsq = Double.MAX_VALUE;
                for (BlockPos p : patrolPoints) {
                    if (p.getX() < cx1 || p.getX() > cx2) continue;
                    if (p.getZ() < cz1 || p.getZ() > cz2) continue;
                    double dx = p.getX() - ccx;
                    double dz = p.getZ() - ccz;
                    double d = dx * dx + dz * dz;
                    if (d < bestDsq) { bestDsq = d; bestCentroid = p; }
                }
                // Если в ячейке нет patrol points — берём любую ближайшую
                if (bestCentroid == null) {
                    for (BlockPos p : patrolPoints) {
                        double dx = p.getX() - ccx;
                        double dz = p.getZ() - ccz;
                        double d = dx * dx + dz * dz;
                        if (d < bestDsq) { bestDsq = d; bestCentroid = p; }
                    }
                }
                centroids.add(bestCentroid);
                cellBounds.add(new int[]{cx1, cx2, cz1, cz2});
                cellIdx++;
            }
        }

        // Распределение patrol points по ячейкам (по координатам)
        List<List<BlockPos>> clusters = new ArrayList<>();
        for (int i = 0; i < n; i++) clusters.add(new ArrayList<>());
        for (BlockPos p : patrolPoints) {
            for (int i = 0; i < n; i++) {
                int[] b = cellBounds.get(i);
                if (p.getX() >= b[0] && p.getX() <= b[1] && p.getZ() >= b[2] && p.getZ() <= b[3]) {
                    clusters.get(i).add(p);
                    break;
                }
            }
        }

        // 3. Назначаем зоны гриверам жадно: каждый гривер берёт ближайший свободный кластер
        Set<Integer> takenClusters = new HashSet<>();
        Map<UUID, Integer> griverToCluster = new HashMap<>();
        // Сортируем гриверов для детерминистичности
        List<UUID> sortedIds = new ArrayList<>(griverIds);
        sortedIds.sort(UUID::compareTo);

        for (UUID id : sortedIds) {
            BlockPos griverPos = griverPositions.get(id);
            int bestClusterIdx = -1;
            double bestDsq = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (takenClusters.contains(i)) continue;
                if (clusters.get(i).isEmpty()) continue;
                double d = griverPos != null ? griverPos.distSqr(centroids.get(i)) : 0;
                if (d < bestDsq) {
                    bestDsq = d;
                    bestClusterIdx = i;
                }
            }
            if (bestClusterIdx == -1) {
                // Свободных непустых кластеров нет — берём любой непустой
                for (int i = 0; i < n; i++) {
                    if (!clusters.get(i).isEmpty()) { bestClusterIdx = i; break; }
                }
            }
            if (bestClusterIdx >= 0) {
                takenClusters.add(bestClusterIdx);
                griverToCluster.put(id, bestClusterIdx);
            }
        }

        // 4. Сохраняем зоны, центроиды и cellBounds в manager
        griverZones.clear();
        griverCentroids.clear();
        griverCellBounds.clear();
        allCentroids.clear();
        allCentroids.addAll(centroids);

        Map<UUID, List<BlockPos>> zones = new HashMap<>();
        for (UUID id : griverIds) {
            Integer idx = griverToCluster.get(id);
            List<BlockPos> zone = (idx != null) ? clusters.get(idx) : new ArrayList<>();
            zones.put(id, zone);
            griverZones.put(id, new HashSet<>(zone));
            if (idx != null) {
                if (centroids.get(idx) != null) griverCentroids.put(id, centroids.get(idx));
                griverCellBounds.put(id, cellBounds.get(idx));
            }
        }

        // Для каждого гривера:
        //   1) Первая цель — ЦЕНТРОИД его зоны (гарантированно глубоко в зоне)
        //   2) Дальше цепочкой по ближайшим точкам зоны
        for (UUID id : griverIds) {
            Integer idx = griverToCluster.get(id);
            if (idx == null) continue;
            List<BlockPos> zone = clusters.get(idx);
            if (zone.isEmpty()) continue;

            BlockPos myCentroid = centroids.get(idx);
            List<BlockPos> myPlan = plans.get(id);
            Set<BlockPos> used = new HashSet<>();

            // Первая цель = centroid (если он в зоне)
            if (myCentroid != null && zone.contains(myCentroid)) {
                myPlan.add(myCentroid);
                used.add(myCentroid);
            }

            BlockPos referencePos = myPlan.isEmpty() ? griverPositions.get(id) : myPlan.get(myPlan.size() - 1);

            for (int i = myPlan.size(); i < planLength; i++) {
                BlockPos best = null;
                double bestDsq = Double.MAX_VALUE;
                for (BlockPos p : zone) {
                    if (used.contains(p)) continue;
                    if (p.equals(referencePos)) continue;
                    double d = p.distSqr(referencePos);
                    if (d < bestDsq) {
                        bestDsq = d;
                        best = p;
                    }
                }
                if (best == null) break;
                myPlan.add(best);
                used.add(best);
                referencePos = best;
            }
        }

        return plans;
    }

    // ========== ВЫБОР СЛУЧАЙНОЙ ТОЧКИ ==========

    /**
     * Выбирает точку для гривера с учётом:
     * - cooldown (не повторять последние N точек)
     * - минимальной дистанции до ПОЗИЦИЙ и ЦЕЛЕЙ других гриверов
     * - избегания точек, занятых другими гриверами
     * <p>
     * Из кандидатов выбирает случайно из top-K с максимальной минимальной дистанцией,
     * чтобы гриверы лучше расходились.
     */
    public BlockPos pickRandomPoint(UUID griverId, BlockPos currentPos, Map<UUID, BlockPos> otherGriverPositions, Random rng) {
        return pickRandomPoint(griverId, currentPos, otherGriverPositions, Collections.emptySet(), rng);
    }

    public BlockPos pickRandomPoint(UUID griverId, BlockPos currentPos, Map<UUID, BlockPos> otherGriverPositions,
                                    Set<BlockPos> alreadyPlannedByMe, Random rng) {
        return pickRandomPoint(griverId, currentPos, otherGriverPositions, alreadyPlannedByMe, rng, false);
    }

    /**
     * @param strictOnly — если true, возвращает null вместо "близких" точек когда нет далёких кандидатов.
     *                     Гарантирует что никакая точка плана не будет близко к другим гриверам.
     */
    public BlockPos pickRandomPoint(UUID griverId, BlockPos currentPos, Map<UUID, BlockPos> otherGriverPositions,
                                    Set<BlockPos> alreadyPlannedByMe, Random rng, boolean strictOnly) {
        if (patrolPoints.isEmpty()) return null;

        // Фиксированные зоны через farthest-point sampling — не зависят от позиций гриверов.
        Set<BlockPos> myZone = griverZones.get(griverId);
        List<BlockPos> pointsToConsider;
        boolean hasZone = myZone != null && !myZone.isEmpty();
        if (hasZone) {
            pointsToConsider = new ArrayList<>(myZone);
        } else {
            pointsToConsider = new ArrayList<>(patrolPoints);
        }

        List<BlockPos> recent = getRecentVisits(griverId);
        Set<BlockPos> takenByOthers = new HashSet<>();
        for (Map.Entry<UUID, BlockPos> e : griverTargets.entrySet()) {
            if (!e.getKey().equals(griverId) && e.getValue() != null) {
                takenByOthers.add(e.getValue());
            }
        }
        takenByOthers.addAll(getAllReservedWaypointsExcept(griverId));
        takenByOthers.addAll(getAllPlannedExcept(griverId));
        takenByOthers.addAll(alreadyPlannedByMe);

        // Точки интереса от которых держимся подальше
        List<BlockPos> avoidPoints = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> e : otherGriverPositions.entrySet()) {
            if (!e.getKey().equals(griverId) && e.getValue() != null) avoidPoints.add(e.getValue());
        }
        for (Map.Entry<UUID, BlockPos> e : griverTargets.entrySet()) {
            if (!e.getKey().equals(griverId) && e.getValue() != null) avoidPoints.add(e.getValue());
        }
        for (Map.Entry<UUID, BlockPos> e : dispersalTargets.entrySet()) {
            if (!e.getKey().equals(griverId) && e.getValue() != null) avoidPoints.add(e.getValue());
        }
        // Planned points других гриверов — держимся подальше
        avoidPoints.addAll(getAllPlannedExcept(griverId));
        // И свои уже запланированные — чтобы очередь разнеслась в пространстве
        avoidPoints.addAll(alreadyPlannedByMe);

        double minSq = (double) minDistanceBetweenGrivers * minDistanceBetweenGrivers;
        double maxDistSq = (double) maxTargetDistance * maxTargetDistance;

        // Жёсткий фильтр: minDistance до других + не дальше maxTargetDistance от гривера
        List<BlockPos> strict = new ArrayList<>();
        // Мягкий: только в радиусе
        List<BlockPos> loose = new ArrayList<>();

        for (BlockPos p : pointsToConsider) {
            if (recent.contains(p)) continue;
            if (takenByOthers.contains(p)) continue;
            if (p.distSqr(currentPos) > maxDistSq) continue;
            loose.add(p);
            // Внутри зоны minDistance НЕ проверяем — зонирование уже гарантирует разнесение
            if (hasZone || minDistToAny(p, avoidPoints) >= minSq) strict.add(p);
        }

        List<BlockPos> pool;
        if (!strict.isEmpty()) {
            pool = strict;
        } else if (strictOnly) {
            pool = new ArrayList<>();
            for (BlockPos p : pointsToConsider) {
                if (p.equals(currentPos)) continue;
                if (takenByOthers.contains(p)) continue;
                if (hasZone || minDistToAny(p, avoidPoints) >= minSq) pool.add(p);
            }
            if (pool.isEmpty()) return null;
        } else {
            if (!loose.isEmpty()) {
                pool = loose;
            } else {
                pool = new ArrayList<>();
                for (BlockPos p : pointsToConsider) {
                    if (p.equals(currentPos)) continue;
                    if (alreadyPlannedByMe.contains(p)) continue;
                    pool.add(p);
                }
                if (pool.isEmpty() && !pointsToConsider.isEmpty()) {
                    return pointsToConsider.get(rng.nextInt(pointsToConsider.size()));
                }
                if (pool.isEmpty()) return null;
            }
        }

        // Сортируем по дистанции до avoidPoints (от самой дальней)
        pool.sort((a, b) -> Double.compare(minDistToAny(b, avoidPoints), minDistToAny(a, avoidPoints)));
        int topK = Math.min(3, pool.size());
        return pool.get(rng.nextInt(topK));
    }

    /** Farthest-point sampling: детерминированно выбирает N максимально разнесённых точек. */
    private List<BlockPos> pickKMeansPlusPlusSeeds(int n, Random rng) {
        List<BlockPos> seeds = new ArrayList<>();
        if (patrolPoints.isEmpty() || n <= 0) return seeds;

        // Первый seed — самая "центральная" точка (минимум суммы расстояний до всех)
        // Это детерминированный старт
        BlockPos firstSeed = patrolPoints.get(0);
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : patrolPoints) {
            double sum = 0;
            for (BlockPos o : patrolPoints) sum += Math.sqrt(p.distSqr(o));
            if (sum < bestScore) {
                bestScore = sum;
                firstSeed = p;
            }
        }
        seeds.add(firstSeed);

        // Далее — FPS: на каждой итерации берём ТОЧКУ с МАКСИМАЛЬНЫМ расстоянием до ближайшего seed'а
        while (seeds.size() < n && seeds.size() < patrolPoints.size()) {
            BlockPos best = null;
            double maxMinD = -1;
            for (BlockPos p : patrolPoints) {
                if (seeds.contains(p)) continue;
                double minD = Double.MAX_VALUE;
                for (BlockPos s : seeds) {
                    double d = p.distSqr(s);
                    if (d < minD) minD = d;
                }
                if (minD > maxMinD) {
                    maxMinD = minD;
                    best = p;
                }
            }
            if (best == null) break;
            seeds.add(best);
        }
        return seeds;
    }

    private double minDistToAny(BlockPos p, List<BlockPos> points) {
        if (points.isEmpty()) return Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        for (BlockPos o : points) {
            double dx = p.getX() - o.getX();
            double dz = p.getZ() - o.getZ();
            double d = dx * dx + dz * dz;
            if (d < min) min = d;
        }
        return min;
    }

    /**
     * Для dispersal-фазы: выбрать точку, максимально далёкую от spawn point и других гриверов.
     */
    public BlockPos pickDispersalPoint(UUID griverId, BlockPos spawnPos, Map<UUID, BlockPos> otherDispersalTargets) {
        if (patrolPoints.isEmpty()) return null;

        double minDist = minDistanceBetweenGrivers;

        BlockPos bestStrict = null;
        double bestStrictScore = -1;
        BlockPos bestLoose = null;
        double bestLooseScore = -1;

        for (BlockPos p : patrolPoints) {
            boolean taken = false;
            for (Map.Entry<UUID, BlockPos> e : otherDispersalTargets.entrySet()) {
                if (!e.getKey().equals(griverId) && p.equals(e.getValue())) {
                    taken = true;
                    break;
                }
            }
            if (taken) continue;

            double distFromSpawn = spawnPos == null ? 0 :
                    Math.sqrt(Math.pow(p.getX() - spawnPos.getX(), 2) + Math.pow(p.getZ() - spawnPos.getZ(), 2));

            double minDistToOthers = Double.MAX_VALUE;
            for (Map.Entry<UUID, BlockPos> e : otherDispersalTargets.entrySet()) {
                if (e.getKey().equals(griverId)) continue;
                if (e.getValue() == null) continue;
                double d = Math.sqrt(Math.pow(p.getX() - e.getValue().getX(), 2) + Math.pow(p.getZ() - e.getValue().getZ(), 2));
                minDistToOthers = Math.min(minDistToOthers, d);
            }
            if (minDistToOthers == Double.MAX_VALUE) minDistToOthers = distFromSpawn;

            double score = distFromSpawn + minDistToOthers * 2.0;

            if (minDistToOthers >= minDist) {
                if (score > bestStrictScore) {
                    bestStrictScore = score;
                    bestStrict = p;
                }
            }
            if (score > bestLooseScore) {
                bestLooseScore = score;
                bestLoose = p;
            }
        }

        return bestStrict != null ? bestStrict : bestLoose;
    }

    // ========== ГРАНИЦЫ ЛАБИРИНТА ==========

    public void setBoundsMin(BlockPos p) { this.boundsMin = p; invalidateMapCache(); setDirty(); }
    public void setBoundsMax(BlockPos p) { this.boundsMax = p; invalidateMapCache(); setDirty(); }
    public BlockPos getBoundsMin() { return boundsMin; }
    public BlockPos getBoundsMax() { return boundsMax; }

    public boolean hasBounds() { return boundsMin != null && boundsMax != null; }

    public boolean isInBounds(BlockPos p) {
        if (!hasBounds()) return true;
        int minX = Math.min(boundsMin.getX(), boundsMax.getX());
        int maxX = Math.max(boundsMin.getX(), boundsMax.getX());
        int minZ = Math.min(boundsMin.getZ(), boundsMax.getZ());
        int maxZ = Math.max(boundsMin.getZ(), boundsMax.getZ());
        return p.getX() >= minX && p.getX() <= maxX && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    public void clearBounds() {
        boundsMin = null;
        boundsMax = null;
        invalidateMapCache();
        setDirty();
    }

    // ========== ЗОНЫ ИСКЛЮЧЕНИЯ ==========

    public void addExclusionZone(BlockPos a, BlockPos b) {
        exclusionZones.add(new ExclusionZone(a, b));
        setDirty();
    }

    public void clearExclusionZones() {
        exclusionZones.clear();
        setDirty();
    }

    public List<ExclusionZone> getExclusionZones() {
        return new ArrayList<>(exclusionZones);
    }

    public boolean isInExclusionZone(BlockPos p) {
        for (ExclusionZone z : exclusionZones) if (z.contains(p)) return true;
        return false;
    }

    public void removeExclusionZoneAt(BlockPos p) {
        exclusionZones.removeIf(z -> z.contains(p));
        setDirty();
    }

    // ========== ГРАФ И A* ПО PATROL POINTS ==========

    private void rebuildPointGraph() {
        pointGraph = new HashMap<>();
        for (BlockPos p : patrolPoints) pointGraph.put(p, new ArrayList<>());
        for (int i = 0; i < patrolPoints.size(); i++) {
            BlockPos a = patrolPoints.get(i);
            for (int j = i + 1; j < patrolPoints.size(); j++) {
                BlockPos b = patrolPoints.get(j);
                double dsq = a.distSqr(b);
                if (dsq <= GRAPH_EDGE_DIST_SQ) {
                    pointGraph.get(a).add(b);
                    pointGraph.get(b).add(a);
                }
            }
        }
    }

    public Map<BlockPos, List<BlockPos>> getPointGraph() {
        if (pointGraph == null) rebuildPointGraph();
        return pointGraph;
    }

    /** Находит цепочку patrol points от start до goal через граф. Возвращает null если не найдено. */
    public List<BlockPos> findWaypointChain(BlockPos start, BlockPos goal) {
        if (patrolPoints.isEmpty()) return null;
        Map<BlockPos, List<BlockPos>> graph = getPointGraph();
        if (graph.isEmpty()) return null;

        BlockPos startNode = nearestPatrolPoint(start);
        BlockPos goalNode = patrolPoints.contains(goal) ? goal : nearestPatrolPoint(goal);
        if (startNode == null || goalNode == null) return null;
        if (startNode.equals(goalNode)) {
            List<BlockPos> single = new ArrayList<>();
            single.add(goalNode);
            return single;
        }

        // A* по графу
        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        PriorityQueue<BlockPos> open = new PriorityQueue<>((a, b) -> {
            double fa = gScore.getOrDefault(a, Double.MAX_VALUE) + Math.sqrt(a.distSqr(goalNode));
            double fb = gScore.getOrDefault(b, Double.MAX_VALUE) + Math.sqrt(b.distSqr(goalNode));
            return Double.compare(fa, fb);
        });
        gScore.put(startNode, 0.0);
        open.add(startNode);
        Set<BlockPos> closed = new HashSet<>();

        while (!open.isEmpty()) {
            BlockPos current = open.poll();
            if (current.equals(goalNode)) {
                LinkedList<BlockPos> path = new LinkedList<>();
                BlockPos c = current;
                while (c != null) {
                    path.addFirst(c);
                    c = cameFrom.get(c);
                }
                return path;
            }
            if (!closed.add(current)) continue;

            for (BlockPos neighbor : graph.getOrDefault(current, Collections.emptyList())) {
                if (closed.contains(neighbor)) continue;
                double tentative = gScore.get(current) + Math.sqrt(current.distSqr(neighbor));
                if (tentative < gScore.getOrDefault(neighbor, Double.MAX_VALUE)) {
                    gScore.put(neighbor, tentative);
                    cameFrom.put(neighbor, current);
                    open.add(neighbor);
                }
            }
        }
        return null;
    }

    private BlockPos nearestPatrolPoint(BlockPos from) {
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (BlockPos p : patrolPoints) {
            double dsq = p.distSqr(from);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = p;
            }
        }
        return best;
    }

    // ========== АВТОГЕНЕРАЦИЯ ТОЧЕК ==========

    /**
     * Сканирует проходы лабиринта в пределах bounds и ставит точки с шагом spacing.
     * Точка = позиция где гривер может стоять (2 пустых блока выше твёрдого пола).
     * Пропускает: вне bounds, в exclusion zones, непроходимые клетки.
     *
     * @return количество добавленных точек
     */
    public int autogeneratePoints(Level level, int spacing, int floorY, int yRadius) {
        if (!hasBounds()) return -1;

        int minX = Math.min(boundsMin.getX(), boundsMax.getX());
        int maxX = Math.max(boundsMin.getX(), boundsMax.getX());
        int minZ = Math.min(boundsMin.getZ(), boundsMax.getZ());
        int maxZ = Math.max(boundsMin.getZ(), boundsMax.getZ());

        int yMin = Math.max(-64, floorY - yRadius);
        int yMax = Math.min(320, floorY + yRadius);

        // СЕТОЧНОЕ СЕМПЛИРОВАНИЕ. Раньше сканировались ВСЕ блоки в bounds rect — для
        // лабиринта 200×200 это 40 000 ячеек × 4 getBlockState = 160 000 лукапов на
        // одной серверной тике, что = 100-200мс залипания и пинг-спайк.
        // Теперь идём по сетке spacing × spacing и для каждой ячейки ищем walkable
        // в небольшом радиусе. Для spacing=8 на 200×200: 25×25 = 625 ячеек × 16 поиск
        // = ~10k лукапов = единицы мс. Результат визуально такой же.
        int step = Math.max(1, spacing);
        int searchRadius = Math.max(1, spacing / 2 - 1);
        int cellSize = step;
        Map<Long, List<BlockPos>> grid = new HashMap<>();
        for (BlockPos p : patrolPoints) addToGrid(grid, p, cellSize);

        int added = 0;
        double spacingSq = (double) spacing * spacing;
        Random rnd = new Random();

        for (int gx = minX; gx <= maxX; gx += step) {
            for (int gz = minZ; gz <= maxZ; gz += step) {
                BlockPos found = null;
                // Сначала пробуем точно центр ячейки
                BlockPos center = findFloor(level, gx, yMin, yMax, gz);
                if (center != null && !isInExclusionZone(center)) {
                    found = center;
                } else {
                    // Спираль вокруг центра в пределах searchRadius
                    outer:
                    for (int r = 1; r <= searchRadius; r++) {
                        for (int dx = -r; dx <= r; dx++) {
                            for (int dz = -r; dz <= r; dz++) {
                                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                                int x = gx + dx;
                                int z = gz + dz;
                                if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                                BlockPos cand = findFloor(level, x, yMin, yMax, z);
                                if (cand != null && !isInExclusionZone(cand)) {
                                    found = cand;
                                    break outer;
                                }
                            }
                        }
                    }
                }

                if (found != null && !isTooCloseGrid(grid, found, cellSize, spacingSq)) {
                    patrolPoints.add(found);
                    addToGrid(grid, found, cellSize);
                    added++;
                }
            }
        }

        if (added > 0) {
            pointGraph = null; // граф устарел
            setDirty();
        }
        return added;
    }

    private BlockPos findFloor(Level level, int x, int yMin, int yMax, int z) {
        for (int y = yMax; y >= yMin; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isValidFloor(level, pos)) return pos;
        }
        return null;
    }

    private boolean isValidFloor(Level level, BlockPos pos) {
        BlockState floor = level.getBlockState(pos.below());
        if (floor.isAir() || !floor.isSolid()) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        return level.getBlockState(pos.above(2)).isAir();
    }

    private void addToGrid(Map<Long, List<BlockPos>> grid, BlockPos p, int cellSize) {
        long key = ((long)(p.getX() / cellSize) << 32) | (p.getZ() / cellSize & 0xFFFFFFFFL);
        grid.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
    }

    private boolean isTooCloseGrid(Map<Long, List<BlockPos>> grid, BlockPos p, int cellSize, double minDistSq) {
        int cx = p.getX() / cellSize;
        int cz = p.getZ() / cellSize;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long key = ((long)(cx + dx) << 32) | ((cz + dz) & 0xFFFFFFFFL);
                List<BlockPos> bucket = grid.get(key);
                if (bucket == null) continue;
                for (BlockPos o : bucket) {
                    double ddx = o.getX() - p.getX();
                    double ddz = o.getZ() - p.getZ();
                    if (ddx * ddx + ddz * ddz < minDistSq) return true;
                }
            }
        }
        return false;
    }

    /** Ищет проход (walkable) в колонне x,z около floorY в пределах +-yRadius. */
    private BlockPos findFloorAt(Level level, int x, int floorY, int z, int yRadius) {
        for (int dy = 0; dy <= yRadius; dy++) {
            for (int sign : new int[]{1, -1}) {
                if (dy == 0 && sign == -1) continue;
                int y = floorY + dy * sign;
                BlockPos base = new BlockPos(x, y, z);
                if (isWalkableFloor(level, base)) return base;
            }
        }
        return null;
    }

    private int countWalkableNeighbors(Level level, BlockPos pos, int yRadius) {
        int count = 0;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(dir);
            if (findFloorAt(level, n.getX(), pos.getY(), n.getZ(), yRadius) != null) count++;
        }
        return count;
    }

    private boolean isWalkableFloor(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.isAir() || !below.isSolid()) return false;
        if (!level.getBlockState(pos).isAir()) return false;
        if (!level.getBlockState(pos.above()).isAir()) return false;
        return level.getBlockState(pos.above(2)).isAir();
    }

    // ========== СОХРАНЕНИЕ / ЗАГРУЗКА ==========

    @Override
    public @NotNull CompoundTag save(CompoundTag tag) {
        tag.putInt("minDistance", minDistanceBetweenGrivers);
        tag.putInt("maxTargetDistance", maxTargetDistance);
        tag.putInt("revisitCooldown", revisitCooldown);
        tag.putLong("emergenceTime", emergenceTime);
        tag.putBoolean("timeBasedEmergence", timeBasedEmergenceEnabled);
        tag.putBoolean("globalPatrolActive", globalPatrolActive);
        tag.putBoolean("globalForceChunkLoading", globalForceChunkLoading);

        if (spawnPoint != null) {
            tag.putInt("spawnX", spawnPoint.getX());
            tag.putInt("spawnY", spawnPoint.getY());
            tag.putInt("spawnZ", spawnPoint.getZ());
        }

        ListTag points = new ListTag();
        for (BlockPos p : patrolPoints) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", p.getX());
            t.putInt("y", p.getY());
            t.putInt("z", p.getZ());
            points.add(t);
        }
        tag.put("points", points);

        if (boundsMin != null && boundsMax != null) {
            tag.putInt("boundsMinX", boundsMin.getX()); tag.putInt("boundsMinY", boundsMin.getY()); tag.putInt("boundsMinZ", boundsMin.getZ());
            tag.putInt("boundsMaxX", boundsMax.getX()); tag.putInt("boundsMaxY", boundsMax.getY()); tag.putInt("boundsMaxZ", boundsMax.getZ());
        }

        ListTag excl = new ListTag();
        for (ExclusionZone z : exclusionZones) excl.add(z.serialize());
        tag.put("exclusionZones", excl);

        ListTag history = new ListTag();
        for (Map.Entry<UUID, LinkedList<BlockPos>> e : griverVisitHistory.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.getKey());
            ListTag hist = new ListTag();
            for (BlockPos p : e.getValue()) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("x", p.getX());
                pt.putInt("y", p.getY());
                pt.putInt("z", p.getZ());
                hist.add(pt);
            }
            t.put("history", hist);
            history.add(t);
        }
        tag.put("visitHistory", history);

        // griverTargets и dispersalTargets не сохраняем — runtime состояние

        // Персистим кэш карты — после рестарта сервера первый заход в админку
        // будет мгновенный (~ms на decode 360КБ NBT vs 4 млн getBlockState).
        if (mapCache != null && mapCache.data != null) {
            CompoundTag mc = new CompoundTag();
            mc.putInt("w", mapCache.width);
            mc.putInt("h", mapCache.height);
            mc.putInt("fy", mapCache.floorY);
            mc.putByteArray("data", mapCache.data);
            mc.putInt("bMinX", mapCache.cachedBoundsMin.getX());
            mc.putInt("bMinY", mapCache.cachedBoundsMin.getY());
            mc.putInt("bMinZ", mapCache.cachedBoundsMin.getZ());
            mc.putInt("bMaxX", mapCache.cachedBoundsMax.getX());
            mc.putInt("bMaxY", mapCache.cachedBoundsMax.getY());
            mc.putInt("bMaxZ", mapCache.cachedBoundsMax.getZ());
            tag.put("mapCache", mc);
        }

        return tag;
    }

    public static PatrolManager load(CompoundTag tag) {
        PatrolManager m = new PatrolManager();
        m.minDistanceBetweenGrivers = tag.contains("minDistance") ? tag.getInt("minDistance") : 50;
        m.maxTargetDistance = tag.contains("maxTargetDistance") ? tag.getInt("maxTargetDistance") : 150;
        m.revisitCooldown = tag.contains("revisitCooldown") ? tag.getInt("revisitCooldown") : 5;
        m.emergenceTime = tag.contains("emergenceTime") ? tag.getLong("emergenceTime") : 13000L;
        m.timeBasedEmergenceEnabled = tag.getBoolean("timeBasedEmergence");
        m.globalPatrolActive = tag.getBoolean("globalPatrolActive");
        m.globalForceChunkLoading = tag.getBoolean("globalForceChunkLoading");

        if (tag.contains("spawnX")) {
            m.spawnPoint = new BlockPos(tag.getInt("spawnX"), tag.getInt("spawnY"), tag.getInt("spawnZ"));
        }

        ListTag points = tag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < points.size(); i++) {
            CompoundTag t = points.getCompound(i);
            m.patrolPoints.add(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
        }

        if (tag.contains("boundsMinX")) {
            m.boundsMin = new BlockPos(tag.getInt("boundsMinX"), tag.getInt("boundsMinY"), tag.getInt("boundsMinZ"));
            m.boundsMax = new BlockPos(tag.getInt("boundsMaxX"), tag.getInt("boundsMaxY"), tag.getInt("boundsMaxZ"));
        }

        ListTag excl = tag.getList("exclusionZones", Tag.TAG_COMPOUND);
        for (int i = 0; i < excl.size(); i++) {
            m.exclusionZones.add(ExclusionZone.deserialize(excl.getCompound(i)));
        }

        ListTag history = tag.getList("visitHistory", Tag.TAG_COMPOUND);
        for (int i = 0; i < history.size(); i++) {
            CompoundTag t = history.getCompound(i);
            if (!t.hasUUID("id")) continue;
            UUID id = t.getUUID("id");
            LinkedList<BlockPos> hist = new LinkedList<>();
            ListTag h = t.getList("history", Tag.TAG_COMPOUND);
            for (int j = 0; j < h.size(); j++) {
                CompoundTag pt = h.getCompound(j);
                hist.add(new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
            }
            m.griverVisitHistory.put(id, hist);
        }

        // Восстанавливаем кэш карты — уберёт лаг при первом открытии админки.
        if (tag.contains("mapCache")) {
            CompoundTag mc = tag.getCompound("mapCache");
            byte[] data = mc.getByteArray("data");
            if (data.length > 0) {
                BlockPos bMin = new BlockPos(mc.getInt("bMinX"), mc.getInt("bMinY"), mc.getInt("bMinZ"));
                BlockPos bMax = new BlockPos(mc.getInt("bMaxX"), mc.getInt("bMaxY"), mc.getInt("bMaxZ"));
                m.mapCache = new MapCache(mc.getInt("w"), mc.getInt("h"), mc.getInt("fy"), data, bMin, bMax);
            }
        }

        return m;
    }

    // ========== ЭКСПОРТ/ИМПОРТ КАРТЫ (внешняя папка) ==========

    public static Path getMapsFolder() {
        Path p = Paths.get(System.getProperty("user.dir"), "config", LabyrinthMod.MOD_ID, "maps");
        try {
            Files.createDirectories(p);
        } catch (Exception e) {
            LabyrinthMod.LOGGER.error("Cannot create maps folder", e);
        }
        return p;
    }

    /** Сохраняет текущее состояние карты в файл внешней папки (для переноса между версиями мода). */
    public CompoundTag exportMapData() {
        CompoundTag root = new CompoundTag();
        root.putInt("minDistance", minDistanceBetweenGrivers);
        root.putInt("revisitCooldown", revisitCooldown);
        root.putLong("emergenceTime", emergenceTime);
        root.putBoolean("timeBasedEmergence", timeBasedEmergenceEnabled);

        if (spawnPoint != null) {
            CompoundTag sp = new CompoundTag();
            sp.putInt("x", spawnPoint.getX());
            sp.putInt("y", spawnPoint.getY());
            sp.putInt("z", spawnPoint.getZ());
            root.put("spawn", sp);
        }

        ListTag points = new ListTag();
        for (BlockPos p : patrolPoints) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", p.getX());
            t.putInt("y", p.getY());
            t.putInt("z", p.getZ());
            points.add(t);
        }
        root.put("points", points);
        return root;
    }

    /** Импортирует карту из NBT (из внешнего файла). */
    public void importMapData(CompoundTag root) {
        if (root.contains("minDistance")) minDistanceBetweenGrivers = root.getInt("minDistance");
        if (root.contains("revisitCooldown")) revisitCooldown = root.getInt("revisitCooldown");
        if (root.contains("emergenceTime")) emergenceTime = root.getLong("emergenceTime");
        if (root.contains("timeBasedEmergence")) timeBasedEmergenceEnabled = root.getBoolean("timeBasedEmergence");

        if (root.contains("spawn")) {
            CompoundTag sp = root.getCompound("spawn");
            spawnPoint = new BlockPos(sp.getInt("x"), sp.getInt("y"), sp.getInt("z"));
        }

        patrolPoints.clear();
        ListTag points = root.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < points.size(); i++) {
            CompoundTag t = points.getCompound(i);
            patrolPoints.add(new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z")));
        }
        setDirty();
    }

    public File getMapFile(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return getMapsFolder().resolve(safe + ".nbt").toFile();
    }
}