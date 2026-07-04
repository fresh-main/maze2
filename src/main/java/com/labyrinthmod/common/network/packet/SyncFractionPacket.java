package com.labyrinthmod.common.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер → клиент: синхронизация фракции игрока (для inventory note и пр.).
 * Шлём при логине, респауне и каждом изменении фракции.
 */
public class SyncFractionPacket {

    public final int fractionId;
    public final String mask;

    public SyncFractionPacket(int fractionId, String mask) {
        this.fractionId = fractionId;
        this.mask = mask == null ? "" : mask;
    }

    public static void encode(SyncFractionPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.fractionId);
        buf.writeUtf(p.mask);
    }

    public static SyncFractionPacket decode(FriendlyByteBuf buf) {
        return new SyncFractionPacket(buf.readInt(), buf.readUtf());
    }

    public static void handle(SyncFractionPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.labyrinthmod.client.ClientPacketHandlers.handleSyncFraction(p)));
        ctx.setPacketHandled(true);
    }
}
