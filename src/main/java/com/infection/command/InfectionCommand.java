package com.infection.command;

import com.infection.capability.InfectionProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public final class InfectionCommand {

    private InfectionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("infection")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                                .executes(InfectionCommand::runSet))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                                .executes(InfectionCommand::runAdd))))
                        .then(Commands.literal("cure")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(InfectionCommand::runCure)))
                        .then(Commands.literal("get")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(InfectionCommand::runGet)))
                        .then(Commands.literal("note")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                                        .executes(InfectionCommand::runNoteSet))))
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(InfectionCommand::runNoteClear))))
        );
    }

    private static int runSet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        for (ServerPlayer p : targets) {
            InfectionProvider.get(p).ifPresent(d -> {
                d.setLevel(value);
                d.syncTo(p);
            });
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("Заражение установлено: " + value + "% для " + targets.size() + " игрок(ов)"), true);
        return targets.size();
    }

    private static int runAdd(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        for (ServerPlayer p : targets) {
            InfectionProvider.get(p).ifPresent(d -> {
                d.setLevel(d.getLevel() + value);
                d.syncTo(p);
            });
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("Изменено заражение на " + value + " у " + targets.size() + " игрок(ов)"), true);
        return targets.size();
    }

    private static int runCure(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            InfectionProvider.get(p).ifPresent(d -> {
                d.setLevel(0);
                d.syncTo(p);
            });
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("Вылечено: " + targets.size() + " игрок(ов)"), true);
        return targets.size();
    }

    private static int runGet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            int level = InfectionProvider.get(p).map(d -> d.getLevel()).orElse(0);
            String custom = InfectionProvider.get(p).map(d -> d.getCustomNoteText()).orElse("");
            ctx.getSource().sendSuccess(() ->
                    Component.literal(p.getGameProfile().getName() + ": " + level + "%"
                            + (custom.isEmpty() ? "" : " | custom: " + (custom.length() > 60 ? custom.substring(0, 60) + "..." : custom))), false);
        }
        return targets.size();
    }

    private static int runNoteSet(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        String text = StringArgumentType.getString(ctx, "text");
        for (ServerPlayer p : targets) {
            InfectionProvider.get(p).ifPresent(d -> {
                d.setCustomNoteText(text);
                d.syncTo(p);
            });
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("Кастомная записка обновлена у " + targets.size() + " игрок(ов)"), true);
        return targets.size();
    }

    private static int runNoteClear(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer p : targets) {
            InfectionProvider.get(p).ifPresent(d -> {
                d.setCustomNoteText("");
                d.syncTo(p);
            });
        }
        ctx.getSource().sendSuccess(() ->
                Component.literal("Кастомная записка стёрта у " + targets.size() + " игрок(ов)"), true);
        return targets.size();
    }
}
