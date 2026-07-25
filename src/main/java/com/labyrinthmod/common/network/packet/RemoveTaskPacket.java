package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RemoveTaskPacket {
    private final BlockPos boardPos;
    private final int index;

    public RemoveTaskPacket(BlockPos boardPos, int index) {
        this.boardPos = boardPos;
        this.index = index;
    }

    public static void encode(RemoveTaskPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.index);
    }

    public static RemoveTaskPacket decode(FriendlyByteBuf buf) {
        return new RemoveTaskPacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(RemoveTaskPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            board.removePreloadedTask(msg.index);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cЗадание удалено из очереди!"));
                        });
            }
        });
        context.setPacketHandled(true);
    }
}