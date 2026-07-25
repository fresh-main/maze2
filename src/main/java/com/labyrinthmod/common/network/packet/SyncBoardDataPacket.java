package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncBoardDataPacket {
    private final BlockPos boardPos;
    private final int spawnIntervalSeconds;
    private final int spawnTimer;
    private final List<CompoundTag> preloadedTasks;

    public SyncBoardDataPacket(BlockPos boardPos, int spawnIntervalSeconds, int spawnTimer, List<CompoundTag> preloadedTasks) {
        this.boardPos = boardPos;
        this.spawnIntervalSeconds = spawnIntervalSeconds;
        this.spawnTimer = spawnTimer;
        this.preloadedTasks = preloadedTasks;
    }

    public static void encode(SyncBoardDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
        buf.writeInt(msg.spawnIntervalSeconds);
        buf.writeInt(msg.spawnTimer);
        buf.writeInt(msg.preloadedTasks.size());
        for (CompoundTag tag : msg.preloadedTasks) {
            buf.writeNbt(tag);
        }
    }

    public static SyncBoardDataPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int interval = buf.readInt();
        int timer = buf.readInt();
        int size = buf.readInt();
        List<CompoundTag> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            tasks.add(buf.readNbt());
        }
        return new SyncBoardDataPacket(pos, interval, timer, tasks);
    }

    public static void handle(SyncBoardDataPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                var level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    var be = level.getBlockEntity(msg.boardPos);
                    if (be instanceof BulletinBoardBlockEntity board) {
                        board.syncDataFromServer(msg.spawnIntervalSeconds, msg.spawnTimer, msg.preloadedTasks);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}