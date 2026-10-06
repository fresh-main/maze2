package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер -> клиент: статичная карта стен/проходов для конкретного MazeMonitor
 * (по UUID блок-энтити). Шлётся один раз при загрузке блока, карта не меняется.
 */
public class MazeMonitorMapPacket {

    public final UUID monitorId;
    public final BlockPos boundsMin;
    public final BlockPos boundsMax;
    public final int mapWidth;
    public final int mapHeight;
    public final int mapFloorY;
    public final byte[] mapData;

    public MazeMonitorMapPacket(UUID monitorId, BlockPos boundsMin, BlockPos boundsMax,
                                int mapWidth, int mapHeight, int mapFloorY, byte[] mapData) {
        this.monitorId = monitorId;
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.mapFloorY = mapFloorY;
        this.mapData = mapData;
    }

    public static void encode(MazeMonitorMapPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.monitorId);
        buf.writeBlockPos(p.boundsMin);
        buf.writeBlockPos(p.boundsMax);
        buf.writeInt(p.mapWidth);
        buf.writeInt(p.mapHeight);
        buf.writeInt(p.mapFloorY);
        buf.writeByteArray(p.mapData);
    }

    public static MazeMonitorMapPacket decode(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        BlockPos min = buf.readBlockPos();
        BlockPos max = buf.readBlockPos();
        int w = buf.readInt();
        int h = buf.readInt();
        int fy = buf.readInt();
        byte[] data = buf.readByteArray();
        return new MazeMonitorMapPacket(id, min, max, w, h, fy, data);
    }

    public static void handle(MazeMonitorMapPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> Proxy.getInstance().handleMazeMonitorMap(p));
        ctx.setPacketHandled(true);
    }
}
