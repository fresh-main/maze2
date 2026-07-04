package com.infection.network.packet;

import com.infection.settings.InfectionSavedData;
import com.infection.settings.InfectionSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SUpdateSettingsPacket(InfectionSettings settings) {

    public static void encode(C2SUpdateSettingsPacket pkt, FriendlyByteBuf buf) {
        pkt.settings.writeTo(buf);
    }

    public static C2SUpdateSettingsPacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateSettingsPacket(InfectionSettings.readFrom(buf));
    }

    public static void handle(C2SUpdateSettingsPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            InfectionSavedData.get(sender.server).replace(pkt.settings, sender.server);
            sender.sendSystemMessage(Component.literal("Настройки заражения обновлены"));
        });
        ctx.get().setPacketHandled(true);
    }
}
