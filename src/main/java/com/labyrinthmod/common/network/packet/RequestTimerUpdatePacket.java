package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class RequestTimerUpdatePacket {
    private final BlockPos boardPos;

    public RequestTimerUpdatePacket(BlockPos boardPos) {
        this.boardPos = boardPos;
    }

    public static void encode(RequestTimerUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
    }

    public static RequestTimerUpdatePacket decode(FriendlyByteBuf buf) {
        return new RequestTimerUpdatePacket(buf.readBlockPos());
    }

    public static void handle(RequestTimerUpdatePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            // Отправляем клиенту актуальные данные
                            board.sendSyncToPlayer(player);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aТаймер обновлён: " + (board.getTicksPerSpawn() - board.getSpawnTimer()) / 20 + " сек"));
                        });
            }
        });
        context.setPacketHandled(true);
    }
}