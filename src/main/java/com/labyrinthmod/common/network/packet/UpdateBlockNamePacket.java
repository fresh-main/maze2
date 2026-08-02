package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.block.entity.NameableSignalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateBlockNamePacket {
    private final BlockPos pos;
    private final String newName;

    public UpdateBlockNamePacket(BlockPos pos, String newName) {
        this.pos = pos;
        this.newName = newName;
    }

    public static void encode(UpdateBlockNamePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.newName, 256);
    }

    public static UpdateBlockNamePacket decode(FriendlyByteBuf buf) {
        return new UpdateBlockNamePacket(buf.readBlockPos(), buf.readUtf(256));
    }

    public static void handle(UpdateBlockNamePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                if (be instanceof NameableSignalBlockEntity signalBe) {
                    signalBe.setCustomName(msg.newName);
                }
            }
        });
        context.setPacketHandled(true);
    }
}