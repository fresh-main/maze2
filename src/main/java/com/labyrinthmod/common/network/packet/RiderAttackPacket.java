package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.entity.GriverEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RiderAttackPacket {

    public RiderAttackPacket() {}

    public static void encode(RiderAttackPacket p, FriendlyByteBuf buf) {}

    public static RiderAttackPacket decode(FriendlyByteBuf buf) { return new RiderAttackPacket(); }

    public static void handle(RiderAttackPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // ТОЛЬКО ОПЕРАТОРЫ могут атаковать с гривера
            if (!player.hasPermissions(2) && !player.isCreative()) return;

            if (player.getVehicle() instanceof GriverEntity g) {
                g.performAttack();
            }
        });
        ctx.setPacketHandled(true);
    }
}