package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.client.screen.TimeSpeedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenTimeSpeedGuiPacket {

    public OpenTimeSpeedGuiPacket() {}

    public OpenTimeSpeedGuiPacket(FriendlyByteBuf buf) {}

    public void encode(FriendlyByteBuf buf) {
        // Пустой пакет, ничего не передаём
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Выполняем на клиенте
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen == null || !(mc.screen instanceof TimeSpeedScreen)) {
                        mc.setScreen(new TimeSpeedScreen());
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка открытия GUI: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });
        context.setPacketHandled(true);
    }
}