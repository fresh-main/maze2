package com.labyrinthmod.common.command;

import com.labyrinthmod.client.screen.TimeSpeedScreen;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class TimeSpeedCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("timespeed")
                        .executes(context -> {
                            // Открываем GUI напрямую на клиенте
                            Minecraft.getInstance().setScreen(new TimeSpeedScreen());
                            return 1;
                        })
        );
    }
}