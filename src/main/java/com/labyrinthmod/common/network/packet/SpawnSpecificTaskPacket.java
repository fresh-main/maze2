package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SpawnSpecificTaskPacket {
    private final BlockPos boardPos;
    private final int taskIndex;

    public SpawnSpecificTaskPacket(BlockPos boardPos, int taskIndex) {
        this.boardPos = boardPos;
        this.taskIndex = taskIndex;
    }

    public static void encode(SpawnSpecificTaskPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.taskIndex);
    }

    public static SpawnSpecificTaskPacket decode(FriendlyByteBuf buf) {
        return new SpawnSpecificTaskPacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(SpawnSpecificTaskPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Ручной спавн удалён
        });
        context.setPacketHandled(true);
    }
}