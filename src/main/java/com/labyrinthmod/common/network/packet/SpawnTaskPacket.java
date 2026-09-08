package com.labyrinthmod.common.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpawnTaskPacket {
    private final BlockPos boardPos;

    public SpawnTaskPacket(BlockPos boardPos) {
        this.boardPos = boardPos;
    }

    public static void encode(SpawnTaskPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
    }

    public static SpawnTaskPacket decode(FriendlyByteBuf buf) {
        return new SpawnTaskPacket(buf.readBlockPos());
    }

    public static void handle(SpawnTaskPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Спавн по таймеру удалён
        });
        context.setPacketHandled(true);
    }
}