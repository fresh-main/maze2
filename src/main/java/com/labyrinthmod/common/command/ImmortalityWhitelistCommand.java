package com.labyrinthmod.common.command;

import com.labyrinthmod.common.event.ImmortalityWhitelist;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = "labyrinthmod")
public class ImmortalityWhitelistCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("immortalwhitelist")
                        .requires(source -> source.hasPermission(2)) // Требует OP права
                        // Добавить игрока в белый список
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            ImmortalityWhitelist.addPlayer(target.getUUID());
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable("labyrinthmod.immortal_whitelist.added", target.getName().getString()),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        // Убрать игрока из белого списка
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            ImmortalityWhitelist.removePlayer(target.getUUID());
                                            context.getSource().sendSuccess(
                                                    () -> Component.translatable("labyrinthmod.immortal_whitelist.removed", target.getName().getString()),
                                                    true
                                            );
                                            return 1;
                                        })
                                )
                        )
                        // Показать всех игроков в белом списке
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    var players = ImmortalityWhitelist.getAll();
                                    if (players.isEmpty()) {
                                        context.getSource().sendSuccess(
                                                () -> Component.translatable("labyrinthmod.immortal_whitelist.empty"),
                                                false
                                        );
                                    } else {
                                        java.util.List<String> names = new java.util.ArrayList<>();
                                        for (UUID uuid : players) {
                                            // Пытаемся получить ник, если игрок онлайн
                                            ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                                            String name = online != null ? online.getName().getString() : uuid.toString().substring(0, 8);
                                            names.add(name);
                                        }
                                        context.getSource().sendSuccess(
                                                () -> Component.translatable("labyrinthmod.immortal_whitelist.list", String.join(", ", names)),
                                                false
                                        );
                                    }
                                    return 1;
                                })
                        )
                        // Очистить весь белый список
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    ImmortalityWhitelist.clear();
                                    context.getSource().sendSuccess(
                                            () -> Component.translatable("labyrinthmod.immortal_whitelist.cleared"),
                                            true
                                    );
                                    return 1;
                                })
                        )
        );
    }
}
