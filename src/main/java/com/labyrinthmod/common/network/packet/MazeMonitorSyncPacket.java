package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: позиции игроков и гриверов для конкретного MazeMonitor
 * (по UUID блок-энтити). Шлётся периодически, пока блок загружен.
 */
public class MazeMonitorSyncPacket {

    public final UUID monitorId;
    public final List<OpenAdminMenuPacket.PlayerSnapshot> players;
    public final List<GriverDot> grivers;

    public static class GriverDot {
        public final BlockPos pos;
        public GriverDot(BlockPos pos) { this.pos = pos; }
    }

    public MazeMonitorSyncPacket(UUID monitorId, List<OpenAdminMenuPacket.PlayerSnapshot> players,
                                 List<GriverDot> grivers) {
        this.monitorId = monitorId;
        this.players = players;
        this.grivers = grivers;
    }

    public static void encode(MazeMonitorSyncPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.monitorId);
        buf.writeInt(p.players.size());
        for (OpenAdminMenuPacket.PlayerSnapshot ps : p.players) {
            buf.writeUUID(ps.id);
            buf.writeUtf(ps.name);
            buf.writeBlockPos(ps.pos);
        }
        buf.writeInt(p.grivers.size());
        for (GriverDot g : p.grivers) buf.writeBlockPos(g.pos);
    }

    public static MazeMonitorSyncPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        int pc = buf.readInt();
        List<OpenAdminMenuPacket.PlayerSnapshot> players = new ArrayList<>(pc);
        for (int i = 0; i < pc; i++) {
            UUID pid = buf.readUUID();
            String name = buf.readUtf();
            BlockPos pos = buf.readBlockPos();
            players.add(new OpenAdminMenuPacket.PlayerSnapshot(pid, name, pos));
        }
        int gc = buf.readInt();
        List<GriverDot> grivers = new ArrayList<>(gc);
        for (int i = 0; i < gc; i++) grivers.add(new GriverDot(buf.readBlockPos()));
        return new MazeMonitorSyncPacket(id, players, grivers);
    }

    public static void handle(MazeMonitorSyncPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> Proxy.getInstance().handleMazeMonitorSync(p));
        ctx.setPacketHandled(true);
    }
}
