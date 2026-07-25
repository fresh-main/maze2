package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AddTaskPacket {
    private final BlockPos boardPos;
    private final CompoundTag taskData;

    public AddTaskPacket(BlockPos boardPos, CompoundTag taskData) {
        this.boardPos = boardPos;
        this.taskData = taskData;
    }

    public static void encode(AddTaskPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeNbt(msg.taskData);
    }

    public static AddTaskPacket decode(FriendlyByteBuf buf) {
        return new AddTaskPacket(buf.readBlockPos(), buf.readNbt());
    }

    public static void handle(AddTaskPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            board.addPreloadedTask(msg.taskData);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aЗадание добавлено в очередь!"));
                        });
            }
        });
        context.setPacketHandled(true);
    }
}