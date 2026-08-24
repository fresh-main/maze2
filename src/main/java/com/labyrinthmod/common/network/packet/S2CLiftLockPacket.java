package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.client.screen.LiftWaitingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class S2CLiftLockPacket {
    private final boolean isLocked;

    public S2CLiftLockPacket(boolean isLocked) {
        this.isLocked = isLocked;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(isLocked);
    }

    public static S2CLiftLockPacket decode(FriendlyByteBuf buf) {
        return new S2CLiftLockPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (isLocked) {
                // ★ ОТКРЫВАЕМ КАСТОМНЫЙ ЭКРАН ОЖИДАНИЯ ★
                mc.setScreen(new LiftWaitingScreen());
            } else {
                // ★ СНЯТИЕ БЛОКИРОВКИ: закрываем экран, если он открыт ★
                if (mc.screen instanceof LiftWaitingScreen) {
                    mc.setScreen(null);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}