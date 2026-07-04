package com.infection.network.packet;

import com.infection.client.ClientInfectionCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record S2CInfectionSyncPacket(UUID playerId, int level, String noteText,
                                     String customNoteText, long hallucinationsSuppressedUntil,
                                     float growthMultiplier, int personalGrowthIntervalTicks) {

    public static void encode(S2CInfectionSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.playerId);
        buf.writeVarInt(pkt.level);
        buf.writeUtf(pkt.noteText == null ? "" : pkt.noteText, 4096);
        buf.writeUtf(pkt.customNoteText == null ? "" : pkt.customNoteText, 4096);
        buf.writeVarLong(pkt.hallucinationsSuppressedUntil);
        buf.writeFloat(pkt.growthMultiplier);
        buf.writeVarInt(pkt.personalGrowthIntervalTicks);
    }

    public static S2CInfectionSyncPacket decode(FriendlyByteBuf buf) {
        return new S2CInfectionSyncPacket(
                buf.readUUID(),
                buf.readVarInt(),
                buf.readUtf(4096),
                buf.readUtf(4096),
                buf.readVarLong(),
                buf.readFloat(),
                buf.readVarInt());
    }

    public static void handle(S2CInfectionSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientInfectionCache.put(pkt.playerId, pkt.level, pkt.noteText,
                                pkt.customNoteText, pkt.hallucinationsSuppressedUntil,
                                pkt.growthMultiplier, pkt.personalGrowthIntervalTicks)));
        ctx.get().setPacketHandled(true);
    }
}
