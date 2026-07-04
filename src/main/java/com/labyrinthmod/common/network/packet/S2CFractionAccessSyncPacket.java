package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.capability.FractionType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Сервер → клиент: текущая карта «фракция → может ли выходить из safe-зоны».
 * Шлётся при открытии {@code FractionAccessScreen} и при каждом изменении
 * любым админом (бродкаст всем операторам онлайн), чтобы экраны были в синхроне.
 */
public class S2CFractionAccessSyncPacket {

    public final Map<FractionType, Boolean> map;
    /** Если true — клиент открывает FractionAccessScreen, если он ещё не открыт.
     *  Если false — только обновляет уже открытый, иначе игнорирует.
     *  Раньше клиент ВСЕГДА открывал screen → при апдейте от другого админа
     *  GUI выскакивал у всех админов на сервере. */
    public final boolean openScreen;

    public S2CFractionAccessSyncPacket(Map<FractionType, Boolean> map, boolean openScreen) {
        this.map = new EnumMap<>(FractionType.class);
        if (map != null) this.map.putAll(map);
        this.openScreen = openScreen;
    }

    /** Совместимость со старыми вызовами — по умолчанию НЕ открывать. */
    public S2CFractionAccessSyncPacket(Map<FractionType, Boolean> map) {
        this(map, false);
    }

    public static void encode(S2CFractionAccessSyncPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.openScreen);
        buf.writeVarInt(pkt.map.size());
        for (Map.Entry<FractionType, Boolean> e : pkt.map.entrySet()) {
            buf.writeUtf(e.getKey().name());
            buf.writeBoolean(e.getValue());
        }
    }

    public static S2CFractionAccessSyncPacket decode(FriendlyByteBuf buf) {
        boolean openScreen = buf.readBoolean();
        int n = buf.readVarInt();
        Map<FractionType, Boolean> map = new EnumMap<>(FractionType.class);
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(64);
            boolean v = buf.readBoolean();
            try {
                map.put(FractionType.valueOf(name), v);
            } catch (IllegalArgumentException ignored) {
                // Неизвестная фракция (старый сейв или другой мод) — пропускаем.
            }
        }
        return new S2CFractionAccessSyncPacket(map, openScreen);
    }

    public static void handle(S2CFractionAccessSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.labyrinthmod.client.ClientPacketHandlers.handleFractionAccessSync(pkt)));
        ctx.setPacketHandled(true);
    }
}
