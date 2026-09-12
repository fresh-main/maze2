package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

        // Обязательно копируем, иначе серверный список может измениться
        // до/во время отправки пакета.
        List<ItemStack> copy = new ArrayList<>(tasks.size());
        for (ItemStack stack : tasks) {
            copy.add(stack.copy());
        }

        this.tasks = copy;
    }

    public static void encode(SyncTasksPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.tasks.size());

        for (ItemStack stack : msg.tasks) {
            buf.writeItem(stack);
        }
    }

    public static SyncTasksPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readInt();

        List<ItemStack> tasks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tasks.add(buf.readItem());
        }

        return new SyncTasksPacket(pos, tasks);
    }

    public static void handle(SyncTasksPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                var level = Minecraft.getInstance().level;

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