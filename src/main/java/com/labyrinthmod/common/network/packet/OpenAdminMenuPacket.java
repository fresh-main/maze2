package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.Proxy;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: открыть админ-меню с полным набором данных + карта лабиринта.
 */
public class OpenAdminMenuPacket {

    // Макс. разрешённый размер карты (чтобы пакет не раздулся)
    private static final int MAX_MAP_SIDE = 600;


    public final List<BlockPos> points;
    public final BlockPos spawnPoint;
    public final int minDistance;
    public final int maxDistance;
    public final int revisitCooldown;
    public final long emergenceTime;
    public final boolean timeBasedEnabled;
    public final boolean patrolActive;
    public final List<GriverSnapshot> grivers;

    // Карта: прямоугольник с проходимостью
    public final BlockPos boundsMin;
    public final BlockPos boundsMax;
    public final int mapWidth;   // X-размер
    public final int mapHeight;  // Z-размер
    public final int mapFloorY;
    public final byte[] mapData; // 0 = стена/непроход, 1 = проход

    public final List<ZoneSnapshot> exclusionZones;
    public final List<PlayerSnapshot> players;
    private final List<MovingSurfaceSnapshot> movingSurfaces;

    public static class PlayerSnapshot {
        public final UUID id;
        public final String name;
        public final BlockPos pos;

        public PlayerSnapshot(UUID id, String name, BlockPos pos) {
            this.id = id; this.name = name; this.pos = pos;
        }
    }

    public static class GriverSnapshot {
        public final UUID id;
        public final int entityId;
        public final BlockPos pos;
        public final float health;
        public final float maxHealth;
        public final BlockPos currentTarget;
        public final List<BlockPos> pathWaypoints;
        public final List<BlockPos> microPath;
        public final int[] zoneBounds; // [minX, maxX, minZ, maxZ] или null

        public GriverSnapshot(UUID id, int entityId, BlockPos pos, float health, float maxHealth,
                              BlockPos currentTarget, List<BlockPos> pathWaypoints, List<BlockPos> microPath,
                              int[] zoneBounds) {
            this.id = id; this.entityId = entityId; this.pos = pos;
            this.health = health; this.maxHealth = maxHealth; this.currentTarget = currentTarget;
            this.pathWaypoints = pathWaypoints == null ? new ArrayList<>() : pathWaypoints;
            this.microPath = microPath == null ? new ArrayList<>() : microPath;
            this.zoneBounds = zoneBounds;
        }
    }

    public static class ZoneSnapshot {
        public final int minX, minZ, maxX, maxZ;
        public ZoneSnapshot(int minX, int minZ, int maxX, int maxZ) {
            this.minX = minX; this.minZ = minZ; this.maxX = maxX; this.maxZ = maxZ;
        }
    }

    // заменить существующий конструктор на этот (он теперь принимает movingSurfaces)
    public OpenAdminMenuPacket(List<BlockPos> points, BlockPos spawnPoint, int minDistance, int maxDistance, int revisitCooldown,
                               long emergenceTime, boolean timeBasedEnabled, boolean patrolActive,
                               List<GriverSnapshot> grivers,
                               BlockPos boundsMin, BlockPos boundsMax,
                               int mapWidth, int mapHeight, int mapFloorY, byte[] mapData,
                               List<ZoneSnapshot> exclusionZones,
                               List<PlayerSnapshot> players,
                               List<MovingSurfaceSnapshot> movingSurfaces) {
        this.points = points; this.spawnPoint = spawnPoint;
        this.minDistance = minDistance; this.maxDistance = maxDistance; this.revisitCooldown = revisitCooldown;
        this.emergenceTime = emergenceTime; this.timeBasedEnabled = timeBasedEnabled;
        this.patrolActive = patrolActive; this.grivers = grivers;
        this.boundsMin = boundsMin; this.boundsMax = boundsMax;
        this.mapWidth = mapWidth; this.mapHeight = mapHeight; this.mapFloorY = mapFloorY;
        this.mapData = mapData;
        this.exclusionZones = exclusionZones;
        this.players = players;
        this.movingSurfaces = movingSurfaces == null ? Collections.emptyList() : movingSurfaces;
    }

    public static OpenAdminMenuPacket fromServer(Level level, PatrolManager m) {
        return fromServer(level, m, -1);
    }

    /**
     * Лёгкий снапшот гриверов и игроков БЕЗ генерации карты.
     * Используется для sync-апдейтов админки — карта (600×600 семплов × 11 getBlockState
     * на ячейку) пересчитывалась на каждый sync, хотя SyncAdminDataPacket её даже не
     * передаёт. Это была главная причина пинг-спайков при кликах в админ-меню.
     */
    public static java.util.List<GriverSnapshot> snapshotGriversLight(
            Level level, PatrolManager m, int selectedEntityId) {
        List<GriverSnapshot> gs = new ArrayList<>();
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                BlockPos tgt = m.getGriverTarget(g.getUUID());
                if (tgt == null) tgt = m.getDispersalTarget(g.getUUID());
                boolean isSelected = (g.getId() == selectedEntityId);
                List<BlockPos> displayPath = isSelected ? g.getDisplayPath() : new ArrayList<>();
                List<BlockPos> microPath = isSelected ? g.getCurrentMicroPath() : new ArrayList<>();
                int[] zoneBounds = isSelected ? m.getGriverCellBounds(g.getUUID()) : null;
                gs.add(new GriverSnapshot(g.getUUID(), g.getId(), g.blockPosition(),
                        g.getHealth(), g.getMaxHealth(), tgt,
                        displayPath, microPath, zoneBounds));
            }
        }
        return gs;
    }

    public static java.util.List<PlayerSnapshot> snapshotPlayersLight(Level level) {
        List<PlayerSnapshot> ps = new ArrayList<>();
        for (var p : level.players()) {
            ps.add(new PlayerSnapshot(p.getUUID(), p.getName().getString(), p.blockPosition()));
        }
        return ps;
    }

    // заменить fromServer(...) на этот вариант — он собирает компактный снимок moving surfaces
    public static OpenAdminMenuPacket fromServer(Level level, PatrolManager m, int selectedEntityId) {
        List<GriverSnapshot> gs = new ArrayList<>();
        AABB area = new AABB(-30000000, -1000, -30000000, 30000000, 1000, 30000000);
        for (var e : level.getEntities(null, area)) {
            if (e instanceof GriverEntity g) {
                BlockPos tgt = m.getGriverTarget(g.getUUID());
                if (tgt == null) tgt = m.getDispersalTarget(g.getUUID());
                boolean isSelected = (g.getId() == selectedEntityId);
                List<BlockPos> displayPath = isSelected ? g.getDisplayPath() : new ArrayList<>();
                List<BlockPos> microPath = isSelected ? g.getCurrentMicroPath() : new ArrayList<>();
                int[] zoneBounds = isSelected ? m.getGriverCellBounds(g.getUUID()) : null;
                gs.add(new GriverSnapshot(g.getUUID(), g.getId(), g.blockPosition(),
                        g.getHealth(), g.getMaxHealth(), tgt,
                        displayPath, microPath, zoneBounds));
            }
        }

        // Карта.
        BlockPos bMin = m.getBoundsMin();
        BlockPos bMax = m.getBoundsMax();
        int mw = 0, mh = 0, fy = 0;
        byte[] data = new byte[0];
        List<MovingSurfaceSnapshot> moving = new ArrayList<>();
        if (bMin != null && bMax != null) {
            PatrolManager.MapCache cache = m.getOrBuildMapCache(level);
            if (cache != null) {
                mw = cache.width;
                mh = cache.height;
                fy = cache.floorY;
                data = cache.data;

                // Собираем moving surfaces (компактно: по колонке сохраняем верхнюю движущуюся поверхность)
                try {
                    int minX = Math.min(bMin.getX(), bMax.getX());
                    int minZ = Math.min(bMin.getZ(), bMax.getZ());
                    int maxX = Math.max(bMin.getX(), bMax.getX());
                    int maxZ = Math.max(bMin.getZ(), bMax.getZ());

                    // AABB поиска — немного расширяем по Y
                    AABB search = new AABB(minX, fy - 64, minZ, maxX, fy + 128, maxZ);

                    Map<Long, MovingSurfaceSnapshot> best = new HashMap<>();
                    // Пытаемся использовать классы contraption'ов (используется в MapScanner)
                    var contraptions = level.getEntitiesOfClass(
                            (Class)Class.forName("com.simibubi.create.content.contraptions.entity.AbstractContraptionEntity"),
                            search, entity -> {
                                try {
                                    return (boolean)entity.getClass().getMethod("isAlive").invoke(entity);
                                } catch (Throwable t) {
                                    return true;
                                }
                            }
                    );

                    for (Object entity : contraptions) {
                        try {
                            // contraption.getBlocks().entrySet()
                            var getContraption = entity.getClass().getMethod("getContraption");
                            Object contr = getContraption.invoke(entity);
                            var getBlocks = contr.getClass().getMethod("getBlocks");
                            @SuppressWarnings("unchecked")
                            java.util.Map<?, ?> blocks = (java.util.Map<?, ?>) getBlocks.invoke(contr);

                            // toGlobalVector(Vec3, float)
                            var toGlobalVector = entity.getClass().getMethod("toGlobalVector", net.minecraft.world.phys.Vec3.class, float.class);

                            for (var entryObj : blocks.entrySet()) {
                                var entry = (java.util.Map.Entry<?, ?>) entryObj;
                                // key: BlockPos-like local pos (in contraption), value: BlockInfo-like with state()
                                Object localPos = entry.getKey();
                                Object blockInfo = entry.getValue();
                                // get BlockState
                                var stateMethod = blockInfo.getClass().getMethod("state");
                                Object stateObj = stateMethod.invoke(blockInfo);
                                if (stateObj == null) continue;

                                // world center
                                // create Vec3.atCenterOf(localPos)
                                // localPos likely is BlockPos; use toString fallback if fails
                                net.minecraft.world.phys.Vec3 center;
                                try {
                                    // create Vec3.atCenterOf
                                    var atCenter = net.minecraft.world.phys.Vec3.class.getMethod("atCenterOf", net.minecraft.core.BlockPos.class);
                                    center = (net.minecraft.world.phys.Vec3) atCenter.invoke(null, localPos);
                                } catch (Throwable ex) {
                                    continue;
                                }

                                net.minecraft.world.phys.Vec3 worldCenter;
                                try {
                                    worldCenter = (net.minecraft.world.phys.Vec3) toGlobalVector.invoke(entity, center, 1.0F);
                                } catch (Throwable ex) {
                                    continue;
                                }

                                int minXi = (int) Math.floor(worldCenter.x - 0.499D);
                                int maxXi = (int) Math.floor(worldCenter.x + 0.499D);
                                int minYi = (int) Math.floor(worldCenter.y - 0.499D);
                                int maxYi = (int) Math.floor(worldCenter.y + 0.499D);
                                int minZi = (int) Math.floor(worldCenter.z - 0.499D);
                                int maxZi = (int) Math.floor(worldCenter.z + 0.499D);

                                for (int x = minXi; x <= maxXi; x++) {
                                    if (x < minX || x > maxX) continue;
                                    for (int z = minZi; z <= maxZi; z++) {
                                        if (z < minZ || z > maxZ) continue;
                                        int yTop = maxYi;
                                        long key = (((long)x) << 32) | (z & 0xFFFFFFFFL);
                                        MovingSurfaceSnapshot prev = best.get(key);
                                        if (prev == null || yTop > prev.y) {
                                            // compute mapColor id
                                            net.minecraft.core.BlockPos cursor = new net.minecraft.core.BlockPos(x, yTop, z);
                                            // stateObj is BlockState-like; try to cast
                                            net.minecraft.world.level.block.state.BlockState bs = (net.minecraft.world.level.block.state.BlockState) stateObj;
                                            net.minecraft.world.level.material.MapColor mc = bs.getMapColor(level, cursor);
                                            int colorId = mc == null ? 0 : mc.col; // fallback: use 'col' or 'id' field (MC versions differ)
                                            // try fields: id or id field name; fallbacks above
                                            try {
                                                // many versions have 'id' int field
                                                java.lang.reflect.Field f = mc.getClass().getField("id");
                                                colorId = f.getInt(mc);
                                            } catch (Throwable ignored) {}
                                            best.put(key, new MovingSurfaceSnapshot(x, z, yTop, colorId));
                                        }
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                            // если API Create недоступен или отражение не сработало — пропускаем этот entity
                        }
                    }

                    moving.addAll(best.values());
                } catch (Throwable t) {
                    // сбор moving surfaces — best-effort. Если что-то упало, просто оставим список пустым.
                    LabyrinthMod.LOGGER.debug("[OpenAdminMenuPacket] collect moving surfaces failed: {}", t.toString());
                }
            }
        }

        List<ZoneSnapshot> zs = new ArrayList<>();
        for (var z : m.getExclusionZones()) {
            zs.add(new ZoneSnapshot(z.minX, z.minZ, z.maxX, z.maxZ));
        }

        List<PlayerSnapshot> ps = new ArrayList<>();
        for (var p : level.players()) {
            ps.add(new PlayerSnapshot(p.getUUID(), p.getName().getString(), p.blockPosition()));
        }

        return new OpenAdminMenuPacket(
                m.getPatrolPoints(), m.getSpawnPoint(),
                m.getMinDistanceBetweenGrivers(), m.getMaxTargetDistance(), m.getRevisitCooldown(),
                m.getEmergenceTime(), m.isTimeBasedEmergenceEnabled(),
                m.isGlobalPatrolActive(), gs,
                bMin, bMax, mw, mh, fy, data, zs, ps, moving
        );
    }

    // заменить encode(...) — после players добавляем movingSurfaces
    public static void encode(OpenAdminMenuPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.points.size());
        for (BlockPos pos : p.points) buf.writeBlockPos(pos);
        buf.writeBoolean(p.spawnPoint != null);
        if (p.spawnPoint != null) buf.writeBlockPos(p.spawnPoint);
        buf.writeInt(p.minDistance);
        buf.writeInt(p.maxDistance);
        buf.writeInt(p.revisitCooldown);
        buf.writeLong(p.emergenceTime);
        buf.writeBoolean(p.timeBasedEnabled);
        buf.writeBoolean(p.patrolActive);
        buf.writeInt(p.grivers.size());
        for (GriverSnapshot g : p.grivers) {
            buf.writeUUID(g.id);
            buf.writeInt(g.entityId);
            buf.writeBlockPos(g.pos);
            buf.writeFloat(g.health);
            buf.writeFloat(g.maxHealth);
            buf.writeBoolean(g.currentTarget != null);
            if (g.currentTarget != null) buf.writeBlockPos(g.currentTarget);
            buf.writeInt(g.pathWaypoints.size());
            for (BlockPos wp : g.pathWaypoints) buf.writeBlockPos(wp);
            buf.writeInt(g.microPath.size());
            for (BlockPos mp : g.microPath) buf.writeBlockPos(mp);
            buf.writeBoolean(g.zoneBounds != null);
            if (g.zoneBounds != null) {
                buf.writeInt(g.zoneBounds[0]);
                buf.writeInt(g.zoneBounds[1]);
                buf.writeInt(g.zoneBounds[2]);
                buf.writeInt(g.zoneBounds[3]);
            }
        }

        buf.writeBoolean(p.boundsMin != null && p.boundsMax != null);
        if (p.boundsMin != null && p.boundsMax != null) {
            buf.writeBlockPos(p.boundsMin);
            buf.writeBlockPos(p.boundsMax);
            buf.writeInt(p.mapWidth);
            buf.writeInt(p.mapHeight);
            buf.writeInt(p.mapFloorY);
            buf.writeByteArray(p.mapData);
        }

        buf.writeInt(p.exclusionZones.size());
        for (ZoneSnapshot z : p.exclusionZones) {
            buf.writeInt(z.minX); buf.writeInt(z.minZ);
            buf.writeInt(z.maxX); buf.writeInt(z.maxZ);
        }

        buf.writeInt(p.players.size());
        for (PlayerSnapshot ps : p.players) {
            buf.writeUUID(ps.id);
            buf.writeUtf(ps.name);
            buf.writeBlockPos(ps.pos);
        }

        // moving surfaces
        buf.writeInt(p.movingSurfaces == null ? 0 : p.movingSurfaces.size());
        if (p.movingSurfaces != null) {
            for (MovingSurfaceSnapshot ms : p.movingSurfaces) {
                buf.writeInt(ms.x);
                buf.writeInt(ms.z);
                buf.writeInt(ms.y);
                buf.writeInt(ms.mapColorId);
            }
        }
    }

    // заменить decode(...) — читаем movingSurfaces в конце
    public static OpenAdminMenuPacket decode(FriendlyByteBuf buf) {
        int pc = buf.readInt();
        List<BlockPos> points = new ArrayList<>(pc);
        for (int i = 0; i < pc; i++) points.add(buf.readBlockPos());
        BlockPos spawn = buf.readBoolean() ? buf.readBlockPos() : null;
        int minDist = buf.readInt();
        int maxDist = buf.readInt();
        int cooldown = buf.readInt();
        long emergence = buf.readLong();
        boolean timeBased = buf.readBoolean();
        boolean active = buf.readBoolean();
        int gc = buf.readInt();
        List<GriverSnapshot> gs = new ArrayList<>(gc);
        for (int i = 0; i < gc; i++) {
            UUID id = buf.readUUID();
            int eid = buf.readInt();
            BlockPos pos = buf.readBlockPos();
            float h = buf.readFloat();
            float mh = buf.readFloat();
            BlockPos tgt = buf.readBoolean() ? buf.readBlockPos() : null;
            int wpc = buf.readInt();
            List<BlockPos> wps = new ArrayList<>(wpc);
            for (int k = 0; k < wpc; k++) wps.add(buf.readBlockPos());
            int mpc = buf.readInt();
            List<BlockPos> mps = new ArrayList<>(mpc);
            for (int k = 0; k < mpc; k++) mps.add(buf.readBlockPos());
            int[] zb = null;
            if (buf.readBoolean()) {
                zb = new int[]{buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()};
            }
            gs.add(new GriverSnapshot(id, eid, pos, h, mh, tgt, wps, mps, zb));
        }

        BlockPos bMin = null, bMax = null;
        int mw = 0, mh2 = 0, fy = 0;
        byte[] data = new byte[0];
        if (buf.readBoolean()) {
            bMin = buf.readBlockPos();
            bMax = buf.readBlockPos();
            mw = buf.readInt();
            mh2 = buf.readInt();
            fy = buf.readInt();
            data = buf.readByteArray();
        }

        int zc = buf.readInt();
        List<ZoneSnapshot> zs = new ArrayList<>(zc);
        for (int i = 0; i < zc; i++) {
            zs.add(new ZoneSnapshot(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()));
        }

        int pc2 = buf.readInt();
        List<PlayerSnapshot> ps = new ArrayList<>(pc2);
        for (int i = 0; i < pc2; i++) {
            UUID pid = buf.readUUID();
            String name = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            ps.add(new PlayerSnapshot(pid, name, pos));
        }

        int mc = buf.readInt();
        List<MovingSurfaceSnapshot> moving = new ArrayList<>(mc);
        for (int i = 0; i < mc; i++) {
            int x = buf.readInt();
            int z = buf.readInt();
            int y = buf.readInt();
            int mapColorId = buf.readInt();
            moving.add(new MovingSurfaceSnapshot(x, z, y, mapColorId));
        }

        return new OpenAdminMenuPacket(points, spawn, minDist, maxDist, cooldown, emergence, timeBased, active, gs,
                bMin, bMax, mw, mh2, fy, data, zs, ps, moving);
    }

    public static void handle(OpenAdminMenuPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            // Безопасный вызов клиентского кода через прокси
            Proxy.getInstance().openAdminScreen(p);
        });
        ctx.setPacketHandled(true);
    }
    // вставить внутри OpenAdminMenuPacket (новый inner-класс)
    public static class MovingSurfaceSnapshot {
        public final int x;
        public final int z;
        public final int y;
        public final int mapColorId;

        public MovingSurfaceSnapshot(int x, int z, int y, int mapColorId) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.mapColorId = mapColorId;
        }
    }
    public static java.util.List<MovingSurfaceSnapshot> collectMovingSurfaces(Level level, BlockPos bMin, BlockPos bMax) {
        List<MovingSurfaceSnapshot> moving = new ArrayList<>();
        if (bMin == null || bMax == null) return moving;
        try {
            int minX = Math.min(bMin.getX(), bMax.getX());
            int minZ = Math.min(bMin.getZ(), bMax.getZ());
            int maxX = Math.max(bMin.getX(), bMax.getX());
            int maxZ = Math.max(bMin.getZ(), bMax.getZ());

            // Search AABB slightly expanded vertically
            AABB search = new AABB(minX, Math.min(bMin.getY(), bMax.getY()) - 64, minZ,
                    maxX, Math.max(bMin.getY(), bMax.getY()) + 128, maxZ);

            // Try to find Create-style contraptions via reflection (best-effort).
            Class<?> contrClass;
            try {
                contrClass = Class.forName("com.simibubi.create.content.contraptions.entity.AbstractContraptionEntity");
            } catch (ClassNotFoundException cnf) {
                // If Create not present — nothing to collect here.
                return moving;
            }

            var contraptions = level.getEntitiesOfClass((Class) contrClass, search,
                    e -> {
                        try {
                            return (boolean) e.getClass().getMethod("isAlive").invoke(e);
                        } catch (Throwable t) {
                            return true;
                        }
                    });

            Map<Long, MovingSurfaceSnapshot> best = new HashMap<>();
            for (Object entity : contraptions) {
                try {
                    // contraption = entity.getContraption()
                    var getContraption = entity.getClass().getMethod("getContraption");
                    Object contr = getContraption.invoke(entity);
                    if (contr == null) continue;

                    // blocks = contr.getBlocks()
                    var getBlocks = contr.getClass().getMethod("getBlocks");
                    @SuppressWarnings("unchecked")
                    Map<?, ?> blocks = (Map<?, ?>) getBlocks.invoke(contr);
                    if (blocks == null) continue;

                    var toGlobalVector = entity.getClass().getMethod("toGlobalVector", net.minecraft.world.phys.Vec3.class, float.class);

                    for (var entryObj : blocks.entrySet()) {
                        var entry = (java.util.Map.Entry<?, ?>) entryObj;
                        Object localPos = entry.getKey();
                        Object blockInfo = entry.getValue();
                        if (localPos == null || blockInfo == null) continue;

                        // world center of block: try Vec3.atCenterOf(localPos) then toGlobalVector
                        net.minecraft.world.phys.Vec3 localCenter;
                        try {
                            localCenter = (net.minecraft.world.phys.Vec3)
                                    net.minecraft.world.phys.Vec3.class.getMethod("atCenterOf", net.minecraft.core.BlockPos.class)
                                            .invoke(null, localPos);
                        } catch (Throwable ex) {
                            continue;
                        }

                        net.minecraft.world.phys.Vec3 worldCenter;
                        try {
                            worldCenter = (net.minecraft.world.phys.Vec3) toGlobalVector.invoke(entity, localCenter, 1.0F);
                        } catch (Throwable ex) {
                            continue;
                        }

                        int minXi = (int) Math.floor(worldCenter.x - 0.499D);
                        int maxYi = (int) Math.floor(worldCenter.y + 0.499D);
                        int minZi = (int) Math.floor(worldCenter.z - 0.499D);

                        // clamp to bounds
                        if (minXi < minX || minXi > maxX || minZi < minZ || minZi > maxZ) continue;

                        long key = (((long) minXi) << 32) | (minZi & 0xFFFFFFFFL);
                        MovingSurfaceSnapshot prev = best.get(key);
                        if (prev == null || maxYi > prev.y) {
                            // mapColorId unused for now — set 0 (we only need coords for overlay)
                            best.put(key, new MovingSurfaceSnapshot(minXi, minZi, maxYi, 0));
                        }
                    }
                } catch (Throwable ignored) {
                    // best-effort: ignore entities we cannot reflect into
                }
            }

            moving.addAll(best.values());
        } catch (Throwable t) {
            LabyrinthMod.LOGGER.debug("[OpenAdminMenuPacket] collectMovingSurfaces failed: {}", t.toString());
        }
        return moving;
    }

}