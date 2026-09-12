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
            if (player == null) return;

            if (player.level().getBlockEntity(msg.boardPos) instanceof BulletinBoardBlockEntity board) {
                boolean success = board.takeTaskAsScroll(msg.slotIndex, player);

                // Если сервер не смог взять задание: слот пуст, нет места в инвентаре,
                // нет тега и т.д. — сразу синкаем клиенту актуальное состояние доски.
                // Иначе клиент может продолжать показывать "несуществующую" карточку.
                if (!success) {
                    board.sendTasksToPlayer(player);
                }
            }
        });

        context.setPacketHandled(true);
    }
}