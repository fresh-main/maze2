package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TakeTaskPacket {
    private final BlockPos boardPos;
    private final int slotIndex;

    public TakeTaskPacket(BlockPos boardPos, int slotIndex) {
        this.boardPos = boardPos;
        this.slotIndex = slotIndex;
    }

    public static void encode(TakeTaskPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.slotIndex);
    }

    public static TakeTaskPacket decode(FriendlyByteBuf buf) {
        return new TakeTaskPacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(TakeTaskPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            board.takeTaskAsScroll(msg.slotIndex, player);
                        });
            }
        });
        context.setPacketHandled(true);
    }
}