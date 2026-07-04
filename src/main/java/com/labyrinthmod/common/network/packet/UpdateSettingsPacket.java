package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.patrol.PatrolManager;
import com.labyrinthmod.common.util.ModLogger;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: обновить настройки (minDistance, cooldown, emergenceTime, toggle).
 */
public class UpdateSettingsPacket {
    public final int minDistance;
    public final int maxDistance;
    public final int revisitCooldown;
    public final long emergenceTime;
    public final boolean timeBasedEnabled;

    public UpdateSettingsPacket(int minDistance, int maxDistance, int revisitCooldown, long emergenceTime, boolean timeBasedEnabled) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.revisitCooldown = revisitCooldown;
        this.emergenceTime = emergenceTime;
        this.timeBasedEnabled = timeBasedEnabled;
    }

    public static void encode(UpdateSettingsPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.minDistance);
        buf.writeInt(p.maxDistance);
        buf.writeInt(p.revisitCooldown);
        buf.writeLong(p.emergenceTime);
        buf.writeBoolean(p.timeBasedEnabled);
    }

    public static UpdateSettingsPacket decode(FriendlyByteBuf buf) {
        return new UpdateSettingsPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(UpdateSettingsPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (!player.hasPermissions(2) && !player.isCreative()) return;
            ServerLevel level = player.serverLevel();
            PatrolManager m = PatrolManager.get(level);
            if (m == null) return;
            ModLogger.admin(player.getName().getString(), "UPDATE_SETTINGS",
                    "min=" + p.minDistance + " max=" + p.maxDistance
                            + " cd=" + p.revisitCooldown + " em=" + p.emergenceTime
                            + " tb=" + p.timeBasedEnabled);

            m.setMinDistanceBetweenGrivers(p.minDistance);
            m.setMaxTargetDistance(p.maxDistance);
            m.setRevisitCooldown(p.revisitCooldown);
            m.setEmergenceTime(p.emergenceTime);
            m.setTimeBasedEmergenceEnabled(p.timeBasedEnabled);

            player.sendSystemMessage(Component.literal(
                    "§a[Patrol] Настройки применены: minDist=" + m.getMinDistanceBetweenGrivers()
                            + ", maxDist=" + m.getMaxTargetDistance()
                            + ", cooldown=" + m.getRevisitCooldown()
                            + ", emergenceTime=" + m.getEmergenceTime()
                            + ", timeBased=" + m.isTimeBasedEmergenceEnabled()));

            // Раньше сервер слал клиенту весь OpenAdminMenuPacket с КАРТОЙ (~360КБ +
            // 4 млн getBlockState на построение). Это и было главной причиной пинг-спайков
            // при изменении настроек. Настройки на самом деле меняются только в полях,
            // которые в SyncAdminDataPacket не идут — но клиент сам уже видит свои значения,
            // так что отдельный sync необязателен. Просто шлём текстовое подтверждение.
        });
        ctx.setPacketHandled(true);
    }
}
