package com.otbor.network;

import com.otbor.client.OtborCreditsScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → all clients: открыть {@link OtborCreditsScreen}.
 * Пустой payload — это просто сигнал.
 */
public class S2COpenCreditsPacket {

    public S2COpenCreditsPacket() {}

    public static void encode(S2COpenCreditsPacket p, FriendlyByteBuf buf) {}

    public static S2COpenCreditsPacket decode(FriendlyByteBuf buf) {
        return new S2COpenCreditsPacket();
    }

    public static void handle(S2COpenCreditsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> OtborCreditsScreen.open()));
        ctx.setPacketHandled(true);
    }
}
