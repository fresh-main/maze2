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

    public OpenAdminMenuPacket(List<BlockPos> points, BlockPos spawnPoint, int minDistance, int maxDistance, int revisitCooldown,
                               long emergenceTime, boolean timeBasedEnabled, boolean patrolActive,
                               List<GriverSnapshot> grivers,
                               BlockPos boundsMin, BlockPos boundsMax,
                               int mapWidth, int mapHeight, int mapFloorY, byte[] mapData,
                               List<ZoneSnapshot> exclusionZones,
                               List<PlayerSnapshot> players) {
        this.points = points; this.spawnPoint = spawnPoint;
        this.minDistance = minDistance; this.maxDistance = maxDistance; this.revisitCooldown = revisitCooldown;
        this.emergenceTime = emergenceTime; this.timeBasedEnabled = timeBasedEnabled;
        this.patrolActive = patrolActive; this.grivers = grivers;
        this.boundsMin = boundsMin; this.boundsMax = boundsMax;
        this.mapWidth = mapWidth; this.mapHeight = mapHeight; this.mapFloorY = mapFloorY;
        this.mapData = mapData;
        this.exclusionZones = exclusionZones;
        this.players = players;
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

    /**
     * @param selectedEntityId entity id ВЫДЕЛЕННОГО гривера в админ-меню. Только для него
     *  строится тяжёлый block-by-block displayPath через A* (4× buildMicroPath = до 100k
     *  итераций). Остальные гриверы шлют пустой path/microPath/zoneBounds — на карте они
     *  всё равно отображаются как одна точка-маркер. До этого fromServer считал пути
     *  для ВСЕХ гриверов на каждый sendSync (каждый клик в админке) → серверный тик
     *  залипал → пинг скакал.
     */
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

        // Карта. Кешируется в PatrolManager — пересобирается только когда меняются
        // bounds. До этого собиралась на каждом открытии меню (до 600×600×11 =
        // 4 млн getBlockState вызовов) → 100-300мс залипания серверного тика.
        BlockPos bMin = m.getBoundsMin();
        BlockPos bMax = m.getBoundsMax();
        int mw = 0, mh = 0, fy = 0;
        byte[] data = new byte[0];
        if (bMin != null && bMax != null) {
            PatrolManager.MapCache cache = m.getOrBuildMapCache(level);
            if (cache != null) {
                mw = cache.width;
                mh = cache.height;
                fy = cache.floorY;
                data = cache.data;
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
                bMin, bMax, mw, mh, fy, data, zs, ps
        );
    }

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
    }

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

        return new OpenAdminMenuPacket(points, spawn, minDist, maxDist, cooldown, emergence, timeBased, active, gs,
                bMin, bMax, mw, mh2, fy, data, zs, ps);
    }

    public static void handle(OpenAdminMenuPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            // Безопасный вызов клиентского кода через прокси
            Proxy.getInstance().openAdminScreen(p);
        });
        ctx.setPacketHandled(true);
    }

}