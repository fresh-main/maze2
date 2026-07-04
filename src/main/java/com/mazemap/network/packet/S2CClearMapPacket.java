package com.mazemap.network.packet;

import com.mazemap.client.ClientMazeMapHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: «сбрось локальный кэш фрагментов карты».
 * Шлётся после команды /mazemap reset.
 */
public class S2CClearMapPacket {

    public S2CClearMapPacket() {}

    public static void encode(S2CClearMapPacket p, FriendlyByteBuf buf) {}

    public static S2CClearMapPacket decode(FriendlyByteBuf buf) {
        return new S2CClearMapPacket();
    }

    public static void handle(S2CClearMapPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientMazeMapHandlers::handleClearMap));
        ctx.setPacketHandled(true);
    }
}
