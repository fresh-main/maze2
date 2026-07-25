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
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            board.spawnTask();
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aЗадание заспавнено!"));
                        });
            }
        });
        context.setPacketHandled(true);
    }
}