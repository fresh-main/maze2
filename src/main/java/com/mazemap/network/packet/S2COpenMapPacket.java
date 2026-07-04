package com.mazemap.network.packet;

import com.mazemap.client.ClientMazeMapHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class S2COpenMapPacket {
    public S2COpenMapPacket() {}
    public static void encode(S2COpenMapPacket p, FriendlyByteBuf buf) {}
    public static S2COpenMapPacket decode(FriendlyByteBuf buf) { return new S2COpenMapPacket(); }

    public static void handle(S2COpenMapPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    // КРИТИЧЕСКИ ВАЖНО: Очищаем кэш от старых карт перед открытием новой!
                    ClientMazeMapHandlers.handleClearMap();
                    ClientMazeMapHandlers.handleOpenMap();
                }));
        ctx.setPacketHandled(true);
    }
}