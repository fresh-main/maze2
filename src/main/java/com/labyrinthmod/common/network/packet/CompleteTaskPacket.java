package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class CompleteTaskPacket {
    private final BlockPos pos;
    private final int slot;

    public CompleteTaskPacket(BlockPos pos, int slot) {
        this.pos = pos;
        this.slot = slot;
    }

    public static void encode(CompleteTaskPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeInt(packet.slot);
    }

    public static CompleteTaskPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int slot = buf.readInt();
        return new CompleteTaskPacket(pos, slot);
    }

    public static void handle(CompleteTaskPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            BlockEntity blockEntity = player.level().getBlockEntity(packet.pos);
            if (blockEntity instanceof BulletinBoardBlockEntity board) {
                // ИСПРАВЛЕНО: Метода completeTask не существует.
                // Используем takeTaskAsScroll для корректного взаимодействия с доской.
                board.takeTaskAsScroll(packet.slot, player);
            }
        });
        context.setPacketHandled(true);
    }
}