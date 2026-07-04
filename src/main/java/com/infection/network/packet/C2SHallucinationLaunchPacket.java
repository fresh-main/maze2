package com.infection.network.packet;

import com.infection.event.MiniEventController;
import com.infection.network.Network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Админ → сервер: запустить «Приступ» (force-hallucination) на конкретных игроков.
 * Точечный таргет — без радиуса, без фазы PREPARING. UUIDs могут быть где угодно
 * на карте (даже разные измерения), сервер сам найдёт ServerPlayer-ов и отошлёт
 * force-packet каждому.
 */
public record C2SHallucinationLaunchPacket(boolean useDefaults,
                                           List<String> customPhrases,
                                           List<UUID> targets) {

    private static final int MAX_PHRASES = 64;
    private static final int MAX_PHRASE_LEN = 256;
    private static final int MAX_TARGETS = 256;

    public static void encode(C2SHallucinationLaunchPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.useDefaults);
        int n = Math.min(pkt.customPhrases.size(), MAX_PHRASES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            String s = pkt.customPhrases.get(i);
            if (s == null) s = "";
            if (s.length() > MAX_PHRASE_LEN) s = s.substring(0, MAX_PHRASE_LEN);
            buf.writeUtf(s, MAX_PHRASE_LEN);
        }
        int t = Math.min(pkt.targets.size(), MAX_TARGETS);
        buf.writeVarInt(t);
        for (int i = 0; i < t; i++) {
            buf.writeUUID(pkt.targets.get(i));
        }
    }

    public static C2SHallucinationLaunchPacket decode(FriendlyByteBuf buf) {
        boolean useDefaults = buf.readBoolean();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_PHRASES) {
            throw new IllegalArgumentException("C2SHallucinationLaunchPacket: invalid phrases count " + n);
        }
        List<String> phrases = new ArrayList<>(n);
        for (int i = 0; i < n; i++) phrases.add(buf.readUtf(MAX_PHRASE_LEN));

        int t = buf.readVarInt();
        if (t < 0 || t > MAX_TARGETS) {
            throw new IllegalArgumentException("C2SHallucinationLaunchPacket: invalid targets count " + t);
        }
        List<UUID> targets = new ArrayList<>(t);
        for (int i = 0; i < t; i++) targets.add(buf.readUUID());

        return new C2SHallucinationLaunchPacket(useDefaults, phrases, targets);
    }

    public static void handle(C2SHallucinationLaunchPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            if (pkt.targets.isEmpty()) {
                sender.displayClientMessage(Component.literal("Не выбрано ни одного игрока."), true);
                return;
            }

            S2CForceHallucinationPacket fwd = new S2CForceHallucinationPacket(
                    pkt.useDefaults, pkt.customPhrases,
                    MiniEventController.HALLUCINATION_DURATION_TICKS);

            int hits = 0;
            for (UUID id : pkt.targets) {
                ServerPlayer target = sender.server.getPlayerList().getPlayer(id);
                if (target == null) continue;
                Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), fwd);
                hits++;
            }

            int finalHits = hits;
            sender.displayClientMessage(Component.literal(
                    "Приступ запущен (" + finalHits + " цел" + (finalHits == 1 ? "ь" : "ей") + ")"), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
