package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetSpawnIntervalPacket {
    private final BlockPos boardPos;
    private final int seconds;

    public SetSpawnIntervalPacket(BlockPos boardPos, int seconds) {
        this.boardPos = boardPos;
        this.seconds = seconds;
    }

    public static void encode(SetSpawnIntervalPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.seconds);
    }

    public static SetSpawnIntervalPacket decode(FriendlyByteBuf buf) {
        return new SetSpawnIntervalPacket(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(SetSpawnIntervalPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Интервалы спавна удалены
        });
        context.setPacketHandled(true);
    }
}