package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncTasksPacket {
    private final BlockPos boardPos;
    private final List<ItemStack> tasks;

    public SyncTasksPacket(BlockPos boardPos, List<ItemStack> tasks) {
        this.boardPos = boardPos;
        this.tasks = tasks;
    }

    public static void encode(SyncTasksPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.tasks.size());
        for (ItemStack stack : msg.tasks) {
            buf.writeNbt(stack.save(new CompoundTag()));
        }
    }

    public static SyncTasksPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readInt();
        List<ItemStack> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            CompoundTag tag = buf.readNbt();
            tasks.add(ItemStack.of(tag));
        }
        return new SyncTasksPacket(pos, tasks);
    }

    public static void handle(SyncTasksPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Находим BlockEntity на клиенте и обновляем его
            if (context.getDirection().getReceptionSide().isClient()) {
                var level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    var be = level.getBlockEntity(msg.boardPos);
                    if (be instanceof BulletinBoardBlockEntity board) {
                        board.syncTasksFromServer(msg.tasks);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}