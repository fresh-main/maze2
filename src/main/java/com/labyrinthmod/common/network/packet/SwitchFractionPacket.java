package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.event.FractionEvents;
import com.labyrinthmod.common.event.FractionSwitcherData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SwitchFractionPacket {
    private final FractionType targetFraction;

    public SwitchFractionPacket() {
        this.targetFraction = null; // Для совместимости
    }

    public SwitchFractionPacket(FractionType targetFraction) {
        this.targetFraction = targetFraction;
    }

    public static void encode(SwitchFractionPacket msg, FriendlyByteBuf buf) {
        if (msg.targetFraction == null) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(msg.targetFraction.name());
        }
    }

    public static SwitchFractionPacket decode(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            String name = buf.readUtf();
            try {
                FractionType type = FractionType.valueOf(name);
                return new SwitchFractionPacket(type);
            } catch (IllegalArgumentException e) {
                return new SwitchFractionPacket();
            }
        }
        return new SwitchFractionPacket();
    }

    public static void handle(SwitchFractionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // Проверка на наличие OP-прав (уровень 2)
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§cУ вас нет прав для переключения фракций!"));
                return;
            }

            player.getCapability(FractionProvider.FRACTION).ifPresent(data -> {
                FractionType current = data.getFraction();
                FractionType next = msg.targetFraction != null ? msg.targetFraction :
                        FractionSwitcherData.getNext(current);

                if (next != current) {
                    data.setFraction(next);

                    // Применяем изменения без анимации (silent)
                    FractionEvents.onFractionChangedSilent(player, current, next);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}