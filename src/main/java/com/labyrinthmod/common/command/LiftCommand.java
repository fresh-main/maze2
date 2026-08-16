package com.labyrinthmod.common.command;

import com.labyrinthmod.common.block.entity.NameableSignalBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class LiftCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lift")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            String targetName = StringArgumentType.getString(context, "name");
                            ServerLevel level = source.getLevel();

                            int activatedCount = 0;

                            List<BlockPos> positions = NameableSignalBlockEntity.getPositionsByName(targetName);

                            for (BlockPos pos : positions) {
                                BlockEntity be = level.getBlockEntity(pos);
                                if (be instanceof NameableSignalBlockEntity signalBe) {
                                    signalBe.setPowered(true);
                                    activatedCount++;
                                }
                            }

                            // ★ ИСПРАВЛЕНО: создаём финальную копию для лямбды ★
                            final int finalCount = activatedCount;

                            if (activatedCount > 0) {
                                source.sendSuccess(() -> Component.literal("Активировано блоков с именем '" + targetName + "': " + finalCount), true);
                            } else {
                                source.sendFailure(Component.literal("Не найдено блоков с именем '" + targetName + "'"));
                            }
                            return 1;
                        })
                )
        );
    }
}