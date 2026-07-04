package com.labyrinthmod.common.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Лёгкий sync админ-данных. Раньше всегда нёс весь список patrolPoints (3000+ штук × 8 байт
 * = 24КБ на каждый sync, и каждый клик в меню), что нагружало сеть и серверный тик. Теперь
 * points/spawn передаются ТОЛЬКО когда реально изменились — флаг pointsIncluded.
 * Клиент при pointsIncluded=false оставляет свой кэш points/spawn неизменным.
 */
public class SyncAdminDataPacket {
    public final List<OpenAdminMenuPacket.GriverSnapshot> grivers;
    public final List<OpenAdminMenuPacket.PlayerSnapshot> players;
    public final boolean patrolActive;
    public final boolean pointsIncluded;
    public final List<BlockPos> points;       // null если pointsIncluded=false
    public final BlockPos spawnPoint;         // null если pointsIncluded=false

    public SyncAdminDataPacket(List<OpenAdminMenuPacket.GriverSnapshot> grivers,
                               List<OpenAdminMenuPacket.PlayerSnapshot> players,
                               boolean patrolActive,
                               List<BlockPos> points,
                               BlockPos spawnPoint) {
        this.grivers = grivers;
        this.players = players;
        this.patrolActive = patrolActive;
        // Backwards-compat ctor: если points передан — всегда включаем.
        this.pointsIncluded = (points != null);
        this.points = points;
        this.spawnPoint = spawnPoint;
    }

    /** Лёгкий конструктор без points (regular sync). */
    public static SyncAdminDataPacket light(List<OpenAdminMenuPacket.GriverSnapshot> grivers,
                                            List<OpenAdminMenuPacket.PlayerSnapshot> players,
                                            boolean patrolActive) {
        return new SyncAdminDataPacket(grivers, players, patrolActive, null, null);
    }

    public static void encode(SyncAdminDataPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.patrolActive);
        buf.writeInt(p.grivers.size());
        for (OpenAdminMenuPacket.GriverSnapshot g : p.grivers) {
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
        buf.writeInt(p.players.size());
        for (OpenAdminMenuPacket.PlayerSnapshot ps : p.players) {
            buf.writeUUID(ps.id);
            buf.writeUtf(ps.name);
            buf.writeBlockPos(ps.pos);
        }
        buf.writeBoolean(p.pointsIncluded);
        if (p.pointsIncluded) {
            buf.writeInt(p.points.size());
            for (BlockPos pt : p.points) buf.writeBlockPos(pt);
            buf.writeBoolean(p.spawnPoint != null);
            if (p.spawnPoint != null) buf.writeBlockPos(p.spawnPoint);
        }
    }

    public static SyncAdminDataPacket decode(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        int n = buf.readInt();
        List<OpenAdminMenuPacket.GriverSnapshot> gs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
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
            gs.add(new OpenAdminMenuPacket.GriverSnapshot(id, eid, pos, h, mh, tgt, wps, mps, zb));
        }
        int pc = buf.readInt();
        List<OpenAdminMenuPacket.PlayerSnapshot> ps = new ArrayList<>(pc);
        for (int i = 0; i < pc; i++) {
            UUID pid = buf.readUUID();
            String name = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            ps.add(new OpenAdminMenuPacket.PlayerSnapshot(pid, name, pos));
        }
        boolean includePoints = buf.readBoolean();
        List<BlockPos> points = null;
        BlockPos spawn = null;
        if (includePoints) {
            int ptc = buf.readInt();
            points = new ArrayList<>(ptc);
            for (int i = 0; i < ptc; i++) points.add(buf.readBlockPos());
            spawn = buf.readBoolean() ? buf.readBlockPos() : null;
        }
        return new SyncAdminDataPacket(gs, ps, active, points, spawn);
    }

    public static void handle(SyncAdminDataPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.labyrinthmod.client.ClientPacketHandlers.handleSyncAdminData(p)));
        ctx.setPacketHandled(true);
    }
}
