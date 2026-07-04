package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.zone.ZoneManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Клиент (админ) → сервер: переключить «может ли фракция выходить из safe-зоны».
 * Действие специальное: только для игроков с permission level 2 или creative.
 * После применения сервер бродкастит {@link S2CFractionAccessSyncPacket} всем
 * операторам онлайн — экраны у других админов синхронизируются.
 */
public record C2SFractionAccessUpdatePacket(String fractionName, boolean canLeave) {

    public static void encode(C2SFractionAccessUpdatePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.fractionName, 64);
        buf.writeBoolean(p.canLeave);
    }

    public static C2SFractionAccessUpdatePacket decode(FriendlyByteBuf buf) {
        return new C2SFractionAccessUpdatePacket(buf.readUtf(64), buf.readBoolean());
    }

    public static void handle(C2SFractionAccessUpdatePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2) && !sender.isCreative()) return;

            FractionType fraction;
            try {
                fraction = FractionType.valueOf(p.fractionName);
            } catch (IllegalArgumentException e) {
                return;
            }
            ServerLevel level = sender.serverLevel();
            ZoneManager manager = ZoneManager.get(level);
            if (manager == null) return;
            manager.setFractionCanLeave(fraction, p.canLeave);

            // Бродкаст обновлённого снапшота всем операторам онлайн БЕЗ openScreen.
            // Те, у кого GUI уже открыт — увидят апдейт live; у кого закрыт — пакет
            // тихо проигнорируется (раньше он внезапно открывал GUI у всех админов).
            S2CFractionAccessSyncPacket sync = new S2CFractionAccessSyncPacket(
                    manager.getFractionAccessSnapshot(), false);
            for (ServerPlayer p2 : sender.server.getPlayerList().getPlayers()) {
                if (p2.hasPermissions(2) || p2.isCreative()) {
                    NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p2), sync);
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
