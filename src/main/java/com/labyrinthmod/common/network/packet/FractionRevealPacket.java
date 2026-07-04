package com.labyrinthmod.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: открыть атмосферный экран «получение роли».
 * Передаём id фракции и (опционально) имя маски — для предателя.
 */
public class FractionRevealPacket {

    public final int fractionId;
    public final String maskFraction; // может быть null/""

    public FractionRevealPacket(int fractionId, String maskFraction) {
        this.fractionId = fractionId;
        this.maskFraction = maskFraction == null ? "" : maskFraction;
    }

    public static void encode(FractionRevealPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.fractionId);
        buf.writeUtf(p.maskFraction);
    }

    public static FractionRevealPacket decode(FriendlyByteBuf buf) {
        int id = buf.readInt();
        String mask = buf.readUtf();
        return new FractionRevealPacket(id, mask);
    }

    public static void handle(FractionRevealPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.labyrinthmod.client.ClientPacketHandlers.handleFractionReveal(p)));
        ctx.setPacketHandled(true);
    }
}
