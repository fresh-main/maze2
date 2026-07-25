package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ResetTimerPacket {
    private final BlockPos boardPos;

    public ResetTimerPacket(BlockPos boardPos) {
        this.boardPos = boardPos;
    }

    public static void encode(ResetTimerPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
    }

    public static ResetTimerPacket decode(FriendlyByteBuf buf) {
        return new ResetTimerPacket(buf.readBlockPos());
    }

    public static void handle(ResetTimerPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            board.resetTimer();
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aТаймер сброшен!"));
                        });
            }
        });
        context.setPacketHandled(true);
    }
}