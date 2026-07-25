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
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            board.spawnSpecificTask(msg.taskIndex);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aЗадание заспавнено мгновенно!"));
                        });
            }
        });
        context.setPacketHandled(true);
    }
}