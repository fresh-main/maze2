package com.infection.network.packet;

import com.infection.event.MiniEventController;
import com.infection.event.MiniEventType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Админ → сервер: запустить таргет-инвентик (LOOMING / FLICKER_PRESENCE)
 * на ОДНОГО конкретного игрока. Точечный, без PREPARING-фазы — стартует сразу
 * в ACTIVE.
 */
public record C2STargetedEventLaunchPacket(int eventTypeOrdinal, UUID targetUUID) {

    public static void encode(C2STargetedEventLaunchPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.eventTypeOrdinal);
        buf.writeUUID(pkt.targetUUID);
    }

    public static C2STargetedEventLaunchPacket decode(FriendlyByteBuf buf) {
        return new C2STargetedEventLaunchPacket(buf.readVarInt(), buf.readUUID());
    }

    public static void handle(C2STargetedEventLaunchPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            ServerPlayer target = sender.server.getPlayerList().getPlayer(pkt.targetUUID);
            if (target == null) {
                sender.displayClientMessage(Component.literal("Цель не найдена."), true);
                return;
            }
            MiniEventController.launchTargeted(sender, MiniEventType.byOrdinal(pkt.eventTypeOrdinal), target);
        });
        ctx.get().setPacketHandled(true);
    }
}
