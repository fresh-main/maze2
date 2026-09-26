package com.labyrinthmod.common.command;

import com.labyrinthmod.common.generation.StructureProtectionHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class LiftProtectionCommand {
    private LiftProtectionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("liftprotection")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> set(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx.getSource(), false)))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource()))));
    }

    private static int set(CommandSourceStack source, boolean enabled) {
        StructureProtectionHandler.setProtectionEnabled(enabled);
        source.sendSuccess(() -> Component.literal(enabled
                ? "§aЗащита структур включена." : "§eЗащита структур выключена."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        boolean enabled = StructureProtectionHandler.isProtectionEnabled();
        source.sendSuccess(() -> Component.literal("Защита структур: " + (enabled ? "§aвключена" : "§cвыключена")), false);
        return enabled ? 1 : 0;
    }
}
