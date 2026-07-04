package com.mazemap.network.packet;

import com.mazemap.network.MazeMapNetwork;
import com.mazemap.storage.MazeMapStorage;
import com.mazemap.storage.PlayerMapData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class C2STransferFragmentsPacket {
    public static final double MAX_TRANSFER_RANGE = 16.0;
    public static final double MAX_TRANSFER_RANGE_SQ = MAX_TRANSFER_RANGE * MAX_TRANSFER_RANGE;
    private final UUID targetUuid;

    public C2STransferFragmentsPacket(UUID targetUuid) { this.targetUuid = targetUuid; }
    public static void encode(C2STransferFragmentsPacket p, FriendlyByteBuf buf) { buf.writeUUID(p.targetUuid); }
    public static C2STransferFragmentsPacket decode(FriendlyByteBuf buf) { return new C2STransferFragmentsPacket(buf.readUUID()); }

    public static void handle(C2STransferFragmentsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || p.targetUuid.equals(sender.getUUID())) return;

            ServerPlayer recipient = sender.serverLevel().getServer().getPlayerList().getPlayer(p.targetUuid);
            if (recipient == null || recipient.level() != sender.level() || recipient.distanceToSqr(sender) > MAX_TRANSFER_RANGE_SQ) {
                sender.displayClientMessage(Component.literal("§eИгрок не найден"), true);
                return;
            }

            PlayerMapData srcData = MazeMapStorage.get(sender.getUUID());
            PlayerMapData dstData = MazeMapStorage.get(recipient.getUUID());
            int updated = 0;

            for (Map.Entry<Long, PlayerMapData.Fragment> e : srcData.getAllFragments().entrySet()) {
                int cellX = PlayerMapData.unpackCellX(e.getKey());
                int cellZ = PlayerMapData.unpackCellZ(e.getKey());
                PlayerMapData.Fragment src = e.getValue();
                PlayerMapData.Fragment dst = dstData.getOrCreateFragment(cellX, cellZ);
                boolean changed = false;

                // Сливаем цвета (pixels)
                for (int i = 0; i < src.pixels.length; i++) {
                    if (src.pixels[i] != 0 && dst.pixels[i] == 0) {
                        dst.pixels[i] = src.pixels[i];
                        changed = true;
                    }
                }
                // Сливаем проходимость (walkable)
                for (int i = 0; i < src.walkable.length; i++) {
                    if ((src.walkable[i] & ~dst.walkable[i]) != 0) {
                        dst.walkable[i] |= src.walkable[i];
                        changed = true;
                    }
                }

                if (changed) {
                    updated++;
                    dstData.markDirty();
                    MazeMapNetwork.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> recipient),
                            new S2CFragmentSyncPacket(cellX, cellZ, dst.pixels, dst.walkable));
                }
            }

            String recipientName = recipient.getName().getString();
            String senderName = sender.getName().getString();
            sender.displayClientMessage(Component.literal("§aПередано §e" + updated + "§a фрагментов §e" + recipientName), true);
            recipient.displayClientMessage(Component.literal("§e" + senderName + "§a показал(а) §6" + updated + "§a фрагмент(ов)"), true);
        });
        ctx.setPacketHandled(true);
    }
}