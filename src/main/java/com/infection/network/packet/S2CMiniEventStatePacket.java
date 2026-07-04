package com.infection.network.packet;

import com.infection.client.minievent.ClientMiniEventState;
import com.infection.event.MiniEventState;
import com.infection.event.MiniEventType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Сервер → клиент: состояние инвентика для конкретного админа.
 *
 *  state=IDLE       — клиент сбрасывает данные по этому админу.
 *  state=PREPARING  — рисуем чёрный силуэт. Без вспышки/звука.
 *  state=ACTIVE     — рисуем чёрный силуэт + вспышка экрана + звук jumpscare.ogg.
 *
 *  durationTicks указывает сколько осталось до конца ACTIVE.
 */
public record S2CMiniEventStatePacket(UUID adminId, int typeOrdinal, int stateOrdinal, int durationTicks) {

    public static void encode(S2CMiniEventStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeUUID(pkt.adminId);
        buf.writeVarInt(pkt.typeOrdinal);
        buf.writeVarInt(pkt.stateOrdinal);
        buf.writeVarInt(pkt.durationTicks);
    }

    public static S2CMiniEventStatePacket decode(FriendlyByteBuf buf) {
        return new S2CMiniEventStatePacket(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CMiniEventStatePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientMiniEventState.apply(pkt.adminId,
                                MiniEventType.byOrdinal(pkt.typeOrdinal),
                                MiniEventState.byOrdinal(pkt.stateOrdinal),
                                pkt.durationTicks)));
        ctx.get().setPacketHandled(true);
    }
}
