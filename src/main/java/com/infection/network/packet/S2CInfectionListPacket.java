package com.infection.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CInfectionListPacket(List<Entry> entries) {

    private static final int MAX_ENTRIES = 256;

    public record Entry(UUID id, String name, int level, String noteText,
                        String customNoteText, long hallucinationsSuppressedUntil,
                        float growthMultiplier, int personalGrowthIntervalTicks) {}

    public static void encode(S2CInfectionListPacket pkt, FriendlyByteBuf buf) {
        int n = Math.min(pkt.entries.size(), MAX_ENTRIES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = pkt.entries.get(i);
            buf.writeUUID(e.id);
            buf.writeUtf(e.name, 64);
            buf.writeVarInt(e.level);
            buf.writeUtf(e.noteText == null ? "" : e.noteText, 4096);
            buf.writeUtf(e.customNoteText == null ? "" : e.customNoteText, 4096);
            buf.writeVarLong(e.hallucinationsSuppressedUntil);
            buf.writeFloat(e.growthMultiplier);
            buf.writeVarInt(e.personalGrowthIntervalTicks);
        }
    }

    public static S2CInfectionListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) {
            throw new IllegalArgumentException("S2CInfectionListPacket: invalid entries count " + n);
        }
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readVarInt(),
                    buf.readUtf(4096),
                    buf.readUtf(4096),
                    buf.readVarLong(),
                    buf.readFloat(),
                    buf.readVarInt()));
        }
        return new S2CInfectionListPacket(list);
    }

    public static void handle(S2CInfectionListPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.infection.client.InfectionListClientHook.openWithEntries(pkt.entries)));
        ctx.get().setPacketHandled(true);
    }
}
