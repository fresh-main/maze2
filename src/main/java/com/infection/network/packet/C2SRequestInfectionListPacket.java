package com.infection.network.packet;

import com.infection.capability.IInfection;
import com.infection.capability.InfectionProvider;
import com.infection.network.Network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record C2SRequestInfectionListPacket() {

    public static void encode(C2SRequestInfectionListPacket pkt, FriendlyByteBuf buf) {
    }

    public static C2SRequestInfectionListPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestInfectionListPacket();
    }

    public static void handle(C2SRequestInfectionListPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            List<S2CInfectionListPacket.Entry> entries = new ArrayList<>();
            for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
                IInfection data = InfectionProvider.get(p).orElse(null);
                int level = data == null ? 0 : data.getLevel();
                String note = data == null ? "" : data.getNoteText();
                String custom = data == null ? "" : data.getCustomNoteText();
                long suppressed = data == null ? 0L : data.getHallucinationsSuppressedUntil();
                float mul = data == null ? 1.0f : data.getGrowthMultiplier();
                int personalInterval = data == null ? 0 : data.getPersonalGrowthIntervalTicks();
                entries.add(new S2CInfectionListPacket.Entry(
                        p.getUUID(), p.getGameProfile().getName(), level, note, custom, suppressed,
                        mul, personalInterval));
            }
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                    new S2CInfectionListPacket(entries));
        });
        ctx.get().setPacketHandled(true);
    }
}
