package com.labyrinthmod.common.command;

import com.labyrinthmod.common.generation.BiomeDebugChat;
import com.labyrinthmod.common.generation.LabyrinthBiomeSource;
import com.labyrinthmod.common.generation.WorldSeedHolder;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

public class BiomeDebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("labydebug")
                        .requires(source -> source.hasPermission(2))

                        .then(Commands.literal("on")
                                .executes(context -> {
                                    BiomeDebugChat.setEnabled(true);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("[LabyDebug] Отладка включена."),
                                            false
                                    );
                                    return 1;
                                })
                        )

                        .then(Commands.literal("off")
                                .executes(context -> {
                                    BiomeDebugChat.setEnabled(false);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("[LabyDebug] Отладка выключена."),
                                            false
                                    );
                                    return 1;
                                })
                        )

                        .then(Commands.literal("status")
                                .executes(context -> {
                                    StringBuilder sb = new StringBuilder();

                                    sb.append("========== Labyrinth Biome Debug ==========\n");
                                    sb.append("enabled=").append(BiomeDebugChat.isEnabled()).append('\n');
                                    sb.append("WorldSeed loaded=").append(WorldSeedHolder.isSeedLoaded());
                                    sb.append(", worldSeed=").append(WorldSeedHolder.getWorldSeed()).append('\n');
                                    sb.append(LabyrinthBiomeSource.getDebugSummary()).append('\n');
                                    sb.append("==========================================");

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(sb.toString()),
                                            false
                                    );

                                    return 1;
                                })
                        )

                        .then(Commands.literal("here")
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();

                                    var level = player.level();
                                    var pos = player.blockPosition();

                                    Holder<Biome> biomeHolder = level.getBiome(pos);

                                    String biomeName = biomeHolder.unwrapKey()
                                            .map(key -> key.location().toString())
                                            .orElse("unknown");

                                    String message =
                                            "[LabyDebug] Позиция: " + pos.toShortString() +
                                                    " | Реальный биом: " + biomeName +
                                                    " | WorldSeed loaded: " + WorldSeedHolder.isSeedLoaded() +
                                                    " | seed: " + WorldSeedHolder.getWorldSeed();

                                    context.getSource().sendSuccess(
                                            () -> Component.literal(message),
                                            false
                                    );

                                    return 1;
                                })
                        )
        );
    }
}