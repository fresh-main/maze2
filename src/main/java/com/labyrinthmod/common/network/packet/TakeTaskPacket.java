package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.item.TaskScrollItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
            if (player != null) {
                player.level().getChunkAt(msg.boardPos).getBlockEntity(msg.boardPos, com.labyrinthmod.common.init.ModBlockEntities.BULLETIN_BOARD_BE.get())
                        .ifPresent(board -> {
                            ItemStack task = board.getTask(msg.slotIndex);
                            if (!task.isEmpty() && task.hasTag()) {
                                // Создаём свиток с данными задания
                                ItemStack scroll = new ItemStack(TaskScrollItem.TASK_SCROLL.get());
                                CompoundTag scrollTag = scroll.getOrCreateTag();
                                scrollTag.putString("Title", task.getTag().getString("Title"));
                                scrollTag.putString("Description", task.getTag().getString("Description"));
                                scrollTag.putString("Reward", task.getTag().getString("Reward"));
                                scrollTag.putString("Author", task.getTag().getString("Author"));
                                scroll.setTag(scrollTag);

                                // Выдаём свиток игроку
                                player.getInventory().add(scroll);

                                // Удаляем задание с доски
                                board.takeTask(msg.slotIndex, player);

                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aЗадание принято! Свиток добавлен в инвентарь."));
                            }
                        });
            }
        });
        context.setPacketHandled(true);
    }
}