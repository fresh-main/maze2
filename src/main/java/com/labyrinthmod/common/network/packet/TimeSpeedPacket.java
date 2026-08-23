package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.event.TimeSpeedHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TimeSpeedPacket {
    private final double speed;
    private final boolean enabled;

    public TimeSpeedPacket(double speed, boolean enabled) {
        this.speed = speed;
        this.enabled = enabled;
    }

    public TimeSpeedPacket(FriendlyByteBuf buf) {
        this.speed = buf.readDouble();
        this.enabled = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(speed);
        buf.writeBoolean(enabled);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Применяем на сервере
            TimeSpeedHandler.setTimeSpeed(speed);
            TimeSpeedHandler.setEnabled(enabled);

            // Логирование для отладки
            System.out.println("[TimeSpeed] Получено значение: speed=" + speed + ", enabled=" + enabled);
            System.out.println("[TimeSpeed] Текущее значение в TimeSpeedHandler: speed=" + TimeSpeedHandler.getTimeSpeed() + ", enabled=" + TimeSpeedHandler.isEnabled());
        });
        context.setPacketHandled(true);
    }
}