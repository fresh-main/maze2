package com.mazemap.network.packet;

import com.mazemap.client.ClientMazeMapHandlers;
import com.mazemap.storage.PlayerMapData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class S2CFragmentSyncPacket {
    public final int cellX;
    public final int cellZ;
    public final byte[] pixels;
    public final byte[] walkable;

    public S2CFragmentSyncPacket(int cellX, int cellZ, byte[] pixels, byte[] walkable) {
        this.cellX = cellX;
        this.cellZ = cellZ;
        this.pixels = pixels;
        this.walkable = walkable;
    }

    public static void encode(S2CFragmentSyncPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.cellX);
        buf.writeInt(p.cellZ);
        buf.writeByteArray(p.pixels);
        buf.writeByteArray(p.walkable);
    }

    public static S2CFragmentSyncPacket decode(FriendlyByteBuf buf) {
        return new S2CFragmentSyncPacket(
                buf.readInt(),
                buf.readInt(),
                buf.readByteArray(), // ИСПРАВЛЕНО: Убрали лимиты
                buf.readByteArray()  // ИСПРАВЛЕНО: Убрали лимиты
        );
    }

    public static void handle(S2CFragmentSyncPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMazeMapHandlers.handleFragmentSync(p)));
        ctx.setPacketHandled(true);
    }
}