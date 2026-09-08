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
            // Таймеры удалены
        });
        context.setPacketHandled(true);
    }
}