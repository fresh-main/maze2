package com.labyrinthmod.common.command;

import com.labyrinthmod.common.block.entity.NamedBlockManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class LiftCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("labyrinth")
                .then(Commands.literal("lift")
                        .executes(LiftCommand::activateLift)
                )
        );
    }

    private static int activateLift(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();

        // Одна строка кода, которая гарантированно найдет все блоки
        int activatedCount = NamedBlockManager.activateByName(level, "lift");

        if (activatedCount > 0) {
            context.getSource().sendSuccess(() ->
                    Component.literal("§aСигнал подан на " + activatedCount + " блок(а) с именем 'lift'."), true);
        } else {
            context.getSource().sendFailure(Component.literal("§cБлоки с именем 'lift' не найдены."));
        }

        return activatedCount;
    }
}