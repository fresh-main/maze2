package com.infection.network.packet;

import com.infection.client.overlay.HallucinationOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сервер → клиент: принудительный приступ галлюцинаций (инвентик HALLUCINATION_SURGE).
 *
 * useDefaults — добавлять ли стандартные фразы из HallucinationOverlay.MESSAGES.
 * customPhrases — фразы, написанные админом в HallucinationConfigScreen.
 * Если useDefaults=false и customPhrases пусто — fallback на дефолтный список.
 */
public record S2CForceHallucinationPacket(boolean useDefaults, List<String> customPhrases, int durationTicks) {

    private static final int MAX_PHRASES = 64;
    private static final int MAX_PHRASE_LEN = 256;

    public static void encode(S2CForceHallucinationPacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.useDefaults);
        int n = Math.min(pkt.customPhrases.size(), MAX_PHRASES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            String s = pkt.customPhrases.get(i);
            if (s == null) s = "";
            if (s.length() > MAX_PHRASE_LEN) s = s.substring(0, MAX_PHRASE_LEN);
            buf.writeUtf(s, MAX_PHRASE_LEN);
        }
        buf.writeVarInt(pkt.durationTicks);
    }

    public static S2CForceHallucinationPacket decode(FriendlyByteBuf buf) {
        boolean useDefaults = buf.readBoolean();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_PHRASES) {
            throw new IllegalArgumentException("S2CForceHallucinationPacket: invalid phrases count " + n);
        }
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(buf.readUtf(MAX_PHRASE_LEN));
        }
        int durationTicks = buf.readVarInt();
        return new S2CForceHallucinationPacket(useDefaults, list, durationTicks);
    }

    public static void handle(S2CForceHallucinationPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        HallucinationOverlay.INSTANCE.forceTrigger(
                                pkt.useDefaults, pkt.customPhrases, pkt.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}
