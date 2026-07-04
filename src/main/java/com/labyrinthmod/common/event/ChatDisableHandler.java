package com.labyrinthmod.common.event;

import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class ChatDisableHandler {

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        boolean isOperator = event.getPlayer().getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR)
                .orElse(false);

        // Если игрок не оператор - отменяем отправку сообщения
        if (!isOperator) {
            event.setCanceled(true);
            // Сообщение просто исчезает, игрок ничего не видит
        }
    }
}