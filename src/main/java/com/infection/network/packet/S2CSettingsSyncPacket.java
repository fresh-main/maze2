package com.infection.network.packet;

import com.infection.settings.ClientSettings;
import com.infection.settings.InfectionSettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CSettingsSyncPacket(InfectionSettings settings) {

    public static void encode(S2CSettingsSyncPacket pkt, FriendlyByteBuf buf) {
        pkt.settings.writeTo(buf);
    }

    public static S2CSettingsSyncPacket decode(FriendlyByteBuf buf) {
        return new S2CSettingsSyncPacket(InfectionSettings.readFrom(buf));
    }

    public static void handle(S2CSettingsSyncPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientSettings.set(pkt.settings)));
        ctx.get().setPacketHandled(true);
    }
}
