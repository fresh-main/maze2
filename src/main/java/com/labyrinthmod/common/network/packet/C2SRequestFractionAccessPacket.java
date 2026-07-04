package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.zone.ZoneManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Клиент → сервер: запрос карты «фракция → может ли выходить из safe-зоны»
 * для открытия {@code FractionAccessScreen}. Сервер ответит {@link S2CFractionAccessSyncPacket}.
 * Должен быть оператором / в creative.
 */
public final class C2SRequestFractionAccessPacket {

    public C2SRequestFractionAccessPacket() {}

    public static void encode(C2SRequestFractionAccessPacket p, FriendlyByteBuf buf) {}

    public static C2SRequestFractionAccessPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestFractionAccessPacket();
    }

    public static void handle(C2SRequestFractionAccessPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2) && !sender.isCreative()) return;
            ServerLevel level = sender.serverLevel();
            ZoneManager manager = ZoneManager.get(level);
            if (manager == null) return;
            // openScreen=true — игрок САМ запросил, сервер открывает у него GUI.
            S2CFractionAccessSyncPacket sync = new S2CFractionAccessSyncPacket(
                    manager.getFractionAccessSnapshot(), true);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), sync);
        });
        ctx.setPacketHandled(true);
    }
}
