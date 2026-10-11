package com.labyrinthmod.common.command;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.block.entity.NameableSignalBlockEntity;
import com.labyrinthmod.common.init.ModSounds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LiftCommand {
    private static final Map<String, Boolean> ACTIVATION_STATES = new ConcurrentHashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("activate")
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
                                playMechanismSound(level, targetName, positions);
                                source.sendSuccess(() -> Component.literal("Активировано блоков: " + finalCount + " (сигнал на 10 тиков)"), true);
                            } else {
                                source.sendFailure(Component.literal("Не найдено блоков с именем '" + targetName + "'"));
                            }
                            return 1;
                        })
                )
        );
    }

    private static void playMechanismSound(ServerLevel level, String targetName, List<BlockPos> positions) {
        boolean secondState = ACTIVATION_STATES.merge(targetName, true, (oldValue, ignored) -> !oldValue);
        SoundEvent event;
        float volume;
        float pitch;

        if ("lift".equals(targetName)) {
            event = (secondState ? ModSounds.LIFT_UP : ModSounds.LIFT_DOWN).get();
            volume = 0.48f;
            pitch = 1.0f;
        } else if (targetName.startsWith("dver_") || targetName.startsWith("sector_")) {
            event = (secondState ? ModSounds.DOOR_OPEN : ModSounds.DOOR_CLOSE).get();
            volume = 0.42f;
            pitch = targetName.startsWith("sector_") ? 0.94f : 1.0f;
        } else {
            return;
        }

        // У одного механизма может быть несколько сигнальных блоков рядом. Один звук
        // на центральной позиции предотвращает неприятное наложение и скачок громкости.
        BlockPos soundPos = positions.get(positions.size() / 2);
        level.playSound(null, soundPos, event, SoundSource.BLOCKS, volume, pitch);
    }
}
