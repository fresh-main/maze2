package com.labyrinthmod.common.command;

import com.labyrinthmod.LabyrinthMod;
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
                            List<BlockPos> positions = NameableSignalBlockEntity.getPositionsByName(targetName, level);

                            for (BlockPos pos : positions) {
                                try {
                                    if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                                        BlockEntity be = level.getBlockEntity(pos);
                                        if (be instanceof NameableSignalBlockEntity signalBe) {
                                            // 1. ВКЛЮЧАЕМ СИГНАЛ
                                            signalBe.setPowered(true);
                                            // 2. ★ УСТАНАВЛИВАЕМ ТАЙМЕР НА 10 ТИКОВ ★
                                            signalBe.setTicksRemaining(10);
                                            activatedCount++;
                                        }
                                    }
                                } catch (Exception e) {
                                    LabyrinthMod.LOGGER.error("Ошибка при активации блока по позиции {}", pos, e);
                                }
                            }

                            final int finalCount = activatedCount;
                            if (activatedCount > 0) {
                                source.sendSuccess(() -> Component.literal("Активировано блоков: " + finalCount + " (сигнал на 10 тиков)"), true);
                            } else {
                                source.sendFailure(Component.literal("Не найдено блоков с именем '" + targetName + "'"));
                            }
                            return 1;
                        })
                )
        );
    }
}