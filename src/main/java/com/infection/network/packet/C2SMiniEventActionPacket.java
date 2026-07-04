package com.infection.network.packet;

import com.infection.event.MiniEventController;
import com.infection.event.MiniEventType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Админ → сервер: действия с инвентиками.
 *  action=0 SELECT  — выбран событие из меню (typeOrdinal указывает какое). Сервер: PREPARING.
 *  action=1 LAUNCH  — хоткей запуска. Сервер: PREPARING → ACTIVE.
 *  action=2 CANCEL  — хоткей отмены. Сервер: PREPARING/ACTIVE → IDLE.
 *  action=3 SMOKE_HIDE — невидим в дыму (внутри SMOKE-ивента или сразу с активацией).
 *  action=4 SMOKE_SHOW — виден из дыма (внутри SMOKE-ивента или сразу с активацией).
 *  action=5 SMOKE_ACTIVATE — активировать SMOKE без toggle invisibility. Click из меню.
 */
public record C2SMiniEventActionPacket(int action, int typeOrdinal) {

    public static final int ACTION_SELECT = 0;
    public static final int ACTION_LAUNCH = 1;
    public static final int ACTION_CANCEL = 2;
    public static final int ACTION_SMOKE_HIDE = 3;
    public static final int ACTION_SMOKE_SHOW = 4;
    public static final int ACTION_SMOKE_ACTIVATE = 5;

    public static void encode(C2SMiniEventActionPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.action);
        buf.writeVarInt(pkt.typeOrdinal);
    }

    public static C2SMiniEventActionPacket decode(FriendlyByteBuf buf) {
        return new C2SMiniEventActionPacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2SMiniEventActionPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!sender.hasPermissions(2)) {
                sender.sendSystemMessage(Component.literal("Требуются права оператора"));
                return;
            }
            switch (pkt.action) {
                case ACTION_SELECT -> MiniEventController.select(sender, MiniEventType.byOrdinal(pkt.typeOrdinal));
                case ACTION_LAUNCH -> MiniEventController.launch(sender);
                case ACTION_CANCEL -> MiniEventController.cancel(sender);
                case ACTION_SMOKE_HIDE -> MiniEventController.smokeHide(sender);
                case ACTION_SMOKE_SHOW -> MiniEventController.smokeShow(sender);
                case ACTION_SMOKE_ACTIVATE -> MiniEventController.smokeActivate(sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
