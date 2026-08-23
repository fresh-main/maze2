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
                                                    () -> Component.literal("§aИгрок " + target.getName().getString() + " добавлен в белый список бессмертия!"),
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
                                                    () -> Component.literal("§cИгрок " + target.getName().getString() + " удалён из белого списка!"),
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
                                                () -> Component.literal("§7Белый список пуст."),
                                                false
                                        );
                                    } else {
                                        StringBuilder sb = new StringBuilder("§aБелый список: ");
                                        for (UUID uuid : players) {
                                            // Пытаемся получить ник, если игрок онлайн
                                            ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(uuid);
                                            String name = online != null ? online.getName().getString() : uuid.toString().substring(0, 8);
                                            sb.append(name).append(", ");
                                        }
                                        context.getSource().sendSuccess(
                                                () -> Component.literal(sb.toString()),
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
                                            () -> Component.literal("§cБелый список очищен!"),
                                            true
                                    );
                                    return 1;
                                })
                        )
        );
    }
}