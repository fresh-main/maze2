package com.infection.network.packet;

import com.infection.capability.InfectionProvider;
import com.infection.settings.InfectionSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Универсальный админ-пакет управления заражением конкретного игрока.
 * action:
 *   0 = SET — выставить уровень в value (0..100)
 *   1 = CURE — мгновенно вылечить (level=0, multiplier=1)
 *   2 = ADD — value к текущему уровню (может быть отрицательным)
 *   3 = SET_MULTIPLIER — установить множитель скорости в value/100f (0 = стоп, 100 = ×1, 3000 = ×30)
 *   4 = FULL — мгновенно полное заражение (level=100, multiplier остаётся)
 *   5 = START — запустить рост: level=max(level, settings.minLevel), multiplier=1
 *   6 = STOP — остановить рост: multiplier=0
 *   7 = SET_PERSONAL_INTERVAL — установить персональный интервал роста value (тики); 0 = глобальный
 */
public record C2SInfectionActionPacket(UUID target, int action, int value) {

    public static final int ACTION_SET = 0;
    public static final int ACTION_CURE = 1;
    public static final int ACTION_ADD = 2;
    public static final int ACTION_SET_MULTIPLIER = 3;
    public static final int ACTION_FULL = 4;
    public static final int ACTION_START = 5;
    public static final int ACTION_STOP = 6;
    public static final int ACTION_SET_PERSONAL_INTERVAL = 7;

    public static void encode(C2SInfectionActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.target);
        buf.writeVarInt(pkt.action);
        buf.writeVarInt(pkt.value);
    }

    public static C2SInfectionActionPacket decode(FriendlyByteBuf buf) {
        return new C2SInfectionActionPacket(buf.readUUID(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2SInfectionActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            ServerPlayer target = sender.server.getPlayerList().getPlayer(pkt.target);
            if (target == null) return;

            InfectionProvider.get(target).ifPresent(data -> {
                switch (pkt.action) {
                    case ACTION_SET -> data.setLevel(pkt.value);
                    case ACTION_CURE -> {
                        data.setLevel(0);
                        data.setGrowthMultiplier(1.0f);
                    }
                    case ACTION_ADD -> data.setLevel(data.getLevel() + pkt.value);
                    case ACTION_SET_MULTIPLIER -> data.setGrowthMultiplier(pkt.value / 100.0f);
                    case ACTION_FULL -> data.setLevel(100);
                    case ACTION_START -> {
                        int minLvl = InfectionSavedData.get(sender.server).settings().minLevel;
                        if (data.getLevel() < minLvl) data.setLevel(minLvl);
                        data.setGrowthMultiplier(1.0f);
                    }
                    case ACTION_STOP -> data.setGrowthMultiplier(0f);
                    case ACTION_SET_PERSONAL_INTERVAL -> data.setPersonalGrowthIntervalTicks(pkt.value);
                }
                data.syncTo(target);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
