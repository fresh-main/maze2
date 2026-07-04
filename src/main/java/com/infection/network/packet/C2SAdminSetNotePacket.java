package com.infection.network.packet;

import com.infection.capability.InfectionProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Админ пишет произвольный текст в кастомное поле записки игрока. Авто-самочувствие
 * по стадии (noteText) при этом продолжает обновляться независимо.
 *
 * Если reset=true — кастомный текст очищается.
 */
public record C2SAdminSetNotePacket(UUID target, String text, boolean reset) {

    public static void encode(C2SAdminSetNotePacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.target);
        buf.writeUtf(pkt.text == null ? "" : pkt.text, 4096);
        buf.writeBoolean(pkt.reset);
    }

    public static C2SAdminSetNotePacket decode(FriendlyByteBuf buf) {
        return new C2SAdminSetNotePacket(buf.readUUID(), buf.readUtf(4096), buf.readBoolean());
    }

    public static void handle(C2SAdminSetNotePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            ServerPlayer target = sender.server.getPlayerList().getPlayer(pkt.target);
            if (target == null) return;

            InfectionProvider.get(target).ifPresent(data -> {
                if (pkt.reset) {
                    data.setCustomNoteText("");
                } else {
                    data.setCustomNoteText(pkt.text == null ? "" : pkt.text);
                }
                data.syncTo(target);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
