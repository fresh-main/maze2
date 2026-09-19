package com.labyrinthmod.common.command;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.config.ModConfig;
import com.labyrinthmod.common.contraption.LabyrinthAssembler;
import com.labyrinthmod.common.data.LabyrinthShiftZone;
import com.labyrinthmod.common.data.LabyrinthZoneSavedData;
import com.labyrinthmod.common.event.ConfigSyncHandler;
import com.labyrinthmod.common.event.LabyrinthShiftAssemblyHandler;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;

public class LabyrinthCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("labyrinth")
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource()))
                )
                .then(Commands.literal("reloadconfig")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadConfig(context.getSource()))
                )
                .then(Commands.literal("shift")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> shiftToggle(context.getSource()))
                        .then(Commands.literal("toggle")
                                .executes(context -> shiftToggle(context.getSource()))
                        )
                        .then(Commands.literal("status")
                                .executes(context -> shiftStatus(context.getSource()))
                        )
                )
        );
    }

    /**
     * Переключает все зоны между вариантами A и B.
     * Работает только на сервере.
     *
     * ВАЖНО: если зона ещё не собрана в контрапцию (contraptionEntityId == null),
     * мы сначала пытаемся собрать её на текущей физической позиции. Если чанки
     * не загружены или блоков нет — зона считается skipped и её isVariantB
     * НЕ переключается, чтобы флаг isVariantB не расходился с реальным
     * физическим положением блоков в мире.
     */
    private static int shiftToggle(CommandSourceStack source) {
        if (source.getServer() == null) {
            source.sendFailure(Component.literal("§cКоманда доступна только на сервере!"));
            return 0;
        }

        // Работаем только с оверворлдом (или текущим измерением, если это лабиринт)
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator)) {
            // Пробуем оверворлд
            level = source.getServer().overworld();
            generator = level.getChunkSource().getGenerator();
            if (!(generator instanceof LabyrinthChunkGenerator)) {
                source.sendFailure(Component.literal(
                        "§cЭто измерение не использует LabyrinthChunkGenerator!"));
                return 0;
            }
        }

        LabyrinthZoneSavedData data = LabyrinthZoneSavedData.get(level);
        if (data.getAllZones().isEmpty()) {
            source.sendFailure(Component.literal("§cНет зон сдвига в этом мире."));
            return 0;
        }

        // Определяем направление переключения по состоянию первой зоны
        boolean anyVariantB = false;
        for (LabyrinthShiftZone zone : data.getAllZones().values()) {
            if (zone.isVariantB) {
                anyVariantB = true;
                break;
            }
        }

        boolean toVariantB = !anyVariantB; // Если все в A — переключаем в B
        int moved = 0;
        int skipped = 0;
        int failed = 0;

        for (LabyrinthShiftZone zone : data.getAllZones().values()) {
            // Определяем индивидуальное направление для каждой зоны
            boolean zoneToB = !zone.isVariantB;

            if (zone.contraptionEntityId == null) {
                // Контрапции нет — сначала пытаемся собрать её на текущей
                // (физической) позиции, прежде чем считать зону перемещённой.
                if (!LabyrinthShiftAssemblyHandler.isAreaLoaded(level, zone.minPos, zone.maxPos)) {
                    skipped++;
                    LabyrinthMod.LOGGER.debug(
                            "[LabyrinthCommand] Zone {} chunks not loaded, skip", zone.id);
                    continue;
                }
                if (!LabyrinthShiftAssemblyHandler.hasBlocksInArea(level, zone.minPos, zone.maxPos)) {
                    skipped++;
                    LabyrinthMod.LOGGER.debug(
                            "[LabyrinthCommand] Zone {} no blocks at {}, skip",
                            zone.id, zone.minPos);
                    continue;
                }
                if (LabyrinthAssembler.assembleZone(level, zone) == null) {
                    failed++;
                    LabyrinthMod.LOGGER.warn(
                            "[LabyrinthCommand] Failed to assemble zone {} before move", zone.id);
                    continue;
                }
            }

            // Контрапция существует (либо только что собрана) — перемещаем её
            boolean success = LabyrinthAssembler.moveZone(level, zone, zoneToB);
            if (success) {
                zone.toggleState();
                moved++;
            } else {
                failed++;
                LabyrinthMod.LOGGER.warn(
                        "[LabyrinthCommand] Failed to move zone {}", zone.id);
            }
        }

        data.setDirty();

        final int finalMoved = moved;
        final int finalSkipped = skipped;
        final int finalFailed = failed;
        final String direction = anyVariantB ? "B → A" : "A → B";

        source.sendSuccess(() -> Component.literal(
                "§aСдвиг лабиринта: §e" + direction +
                        " §a| Перемещено: §f" + finalMoved +
                        " §7Пропущено: §f" + finalSkipped +
                        " §cОшибок: §f" + finalFailed), true);

        LabyrinthMod.LOGGER.info(
                "[LabyrinthCommand] Labyrinth shift {} executed by {}. Moved: {}, Skipped: {}, Failed: {}",
                direction, source.getTextName(), moved, skipped, failed);

        return moved;
    }

    /**
     * Показывает текущее состояние зон сдвига.
     */
    private static int shiftStatus(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator)) {
            level = source.getServer().overworld();
            generator = level.getChunkSource().getGenerator();
            if (!(generator instanceof LabyrinthChunkGenerator)) {
                source.sendFailure(Component.literal(
                        "§cЭто измерение не использует LabyrinthChunkGenerator!"));
                return 0;
            }
        }

        LabyrinthZoneSavedData data = LabyrinthZoneSavedData.get(level);
        int total = data.getAllZones().size();
        int variantB = 0;
        int assembled = 0;

        for (LabyrinthShiftZone zone : data.getAllZones().values()) {
            if (zone.isVariantB) variantB++;
            if (zone.contraptionEntityId != null) assembled++;
        }
        final int fVariantB = variantB;
        final int fAssembled = assembled;

        source.sendSuccess(() -> Component.literal("§6=== Labyrinth Shift Status ==="), false);
        source.sendSuccess(() -> Component.literal(
                "§7Всего зон: §f" + total), false);
        source.sendSuccess(() -> Component.literal(
                "§7В варианте B: §f" + fVariantB + " §7/ в варианте A: §f" + (total - fVariantB)), false);
        source.sendSuccess(() -> Component.literal(
                "§7Собрано контрапций: §f" + fAssembled + " §7/ " + total), false);

        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§6§l=== LABYRINTH MOD COMMANDS ==="), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§e§l【Сдвиг стен】"), false);
        source.sendSuccess(() -> Component.literal(
                "§7/labyrinth shift §8- §fПереключить все стены A ↔ B"), false);
        source.sendSuccess(() -> Component.literal(
                "§7/labyrinth shift toggle §8- §fТо же самое"), false);
        source.sendSuccess(() -> Component.literal(
                "§7/labyrinth shift status §8- §fПоказать состояние зон"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("§e§l【Основные】"), false);
        source.sendSuccess(() -> Component.literal(
                "§7/labyrinth help §8- §fПоказать эту справку"), false);
        source.sendSuccess(() -> Component.literal(
                "§7/labyrinth reloadconfig §8- §fПерезагрузить конфиг"), false);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        ModConfig.reload();
        if (source.getEntity() instanceof ServerPlayer player) {
            ConfigSyncHandler.syncToAllPlayers(player);
        }
        source.sendSuccess(
                () -> Component.literal("§aКонфиг перезагружен и синхронизирован с клиентами!"), true);
        return 1;
    }
}