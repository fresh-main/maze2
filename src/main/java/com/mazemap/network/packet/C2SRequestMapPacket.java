package com.mazemap.network.packet;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.MazeMapNetwork;
import com.mazemap.storage.PlayerMapData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.function.Supplier;

public final class C2SRequestMapPacket {
    public C2SRequestMapPacket() {}
    public static void encode(C2SRequestMapPacket p, FriendlyByteBuf buf) {}
    public static C2SRequestMapPacket decode(FriendlyByteBuf buf) { return new C2SRequestMapPacket(); }

    public static void handle(C2SRequestMapPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            ItemStack mapStack = PersonalMapItem.findInInventory(sender);
            if (mapStack.isEmpty()) {
                LabyrinthMod.LOGGER.warn("[MazeMap] C2SRequestMap but player has no map item");
                return;
            }

            // Читаем данные именно из этого предмета
            PlayerMapData data = PersonalMapItem.getData(mapStack);
            Map<Long, PlayerMapData.Fragment> all = data.getAllFragments();

            // 1. СНАЧАЛА очищаем кэш клиента и открываем GUI
            MazeMapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new S2COpenMapPacket());

            // 2. ПОТОМ отправляем фрагменты
            for (Map.Entry<Long, PlayerMapData.Fragment> e : all.entrySet()) {
                long key = e.getKey();
                int cellX = (int) (key >> 32);
                int cellZ = (int) key;
                PlayerMapData.Fragment frag = e.getValue();
                MazeMapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender),
                        new S2CFragmentSyncPacket(cellX, cellZ, frag.pixels, frag.walkable));
            }
        });
        ctx.setPacketHandled(true);
    }
}