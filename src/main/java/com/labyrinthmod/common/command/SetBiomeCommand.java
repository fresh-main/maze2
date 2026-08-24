package com.labyrinthmod.common.command;

import com.labyrinthmod.common.generation.LabyrinthBiomeSource;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SetBiomeCommand {

    // Максимальное количество чанков за одну операцию.
    // Если хочешь больше — увеличь.
    private static final int MAX_CHUNKS = 4096;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("setbiome")
                        .requires(source -> source.hasPermission(2))

                        // Очистка переопределения
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    LabyrinthBiomeSource.clearOverride();

                                    context.getSource().sendSuccess(
                                            () -> Component.literal("[SetBiome] Override очищен."),
                                            true
                                    );

                                    return 1;
                                })
                        )

                        // Только переопределение для будущих чанков
                        // /setbiome override <точка1> <точка2> <биом>
                        .then(Commands.literal("override")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .then(Commands.argument("biome", StringArgumentType.greedyString())
                                                        .executes(context -> executeOverride(
                                                                context.getSource(),
                                                                BlockPosArgument.getBlockPos(context, "from"),
                                                                BlockPosArgument.getBlockPos(context, "to"),
                                                                StringArgumentType.getString(context, "biome")
                                                        ))
                                                )
                                        )
                                )
                        )

                        // Перезаполнить биомы из генератора без принудительного биома
                        // /setbiome regen <точка1> <точка2>
                        .then(Commands.literal("regen")
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(context -> executeRegenerate(
                                                        context.getSource(),
                                                        BlockPosArgument.getBlockPos(context, "from"),
                                                        BlockPosArgument.getBlockPos(context, "to")
                                                ))
                                        )
                                )
                        )

                        // Установить биом через override и сразу перезаполнить чанки
                        // /setbiome <точка1> <точка2> <биом>
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .then(Commands.argument("biome", StringArgumentType.greedyString())
                                                .executes(context -> executeSetAndRegenerate(
                                                        context.getSource(),
                                                        BlockPosArgument.getBlockPos(context, "from"),
                                                        BlockPosArgument.getBlockPos(context, "to"),
                                                        StringArgumentType.getString(context, "biome")
                                                ))
                                        )
                                )
                        )
        );
    }

    private static int executeOverride(CommandSourceStack source, BlockPos from, BlockPos to, String biomeName)
            throws CommandSyntaxException {

        ServerLevel level = source.getLevel();
        Holder<Biome> holder = getBiomeHolder(level, biomeName);

        LabyrinthBiomeSource.setOverride(from, to, holder);

        source.sendSuccess(
                () -> Component.literal(
                        "[SetBiome] Override установлен. Новые чанки в этой зоне будут получать биом " +
                                biomeName + "."
                ),
                true
        );

        source.sendSuccess(
                () -> Component.literal(
                        "[SetBiome] Уже сгенерированные чанки не изменятся, пока ты не сделаешь /setbiome regen."
                ),
                false
        );

        return 1;
    }

    private static int executeRegenerate(CommandSourceStack source, BlockPos from, BlockPos to)
            throws CommandSyntaxException {

        int changedChunks = regenerateBiomesFromGenerator(source.getLevel(), from, to);

        source.sendSuccess(
                () -> Component.literal(
                        "[SetBiome] Биомы перезаполнены из генератора. Чанков обработано: " + changedChunks + "."
                ),
                true
        );

        return 1;
    }

    private static int executeSetAndRegenerate(CommandSourceStack source, BlockPos from, BlockPos to, String biomeName)
            throws CommandSyntaxException {

        ServerLevel level = source.getLevel();
        Holder<Biome> holder = getBiomeHolder(level, biomeName);

        // Сначала включаем переопределение в генераторе биомов.
        LabyrinthBiomeSource.setOverride(from, to, holder);

        // Затем перезаполняем существующие чанки через fillBiomesFromNoise.
        int changedChunks = regenerateBiomesFromGenerator(level, from, to);

        source.sendSuccess(
                () -> Component.literal(
                        "[SetBiome] Биом " + biomeName + " установлен через генератор. Чанков обработано: " +
                                changedChunks + "."
                ),
                true
        );

        source.sendSuccess(
                () -> Component.literal(
                        "[SetBiome] Если не видно изменений, перезагрузи чанки или перезайди в мир."
                ),
                false
        );

        return 1;
    }

    private static int regenerateBiomesFromGenerator(ServerLevel level, BlockPos from, BlockPos to)
            throws CommandSyntaxException {

        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());

        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);

        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        long chunkCount = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);

        if (chunkCount > MAX_CHUNKS) {
            throw error(
                    "Слишком большая зона: " + chunkCount + " чанков. Максимум: " + MAX_CHUNKS +
                            ". Уменьши область."
            );
        }

        ServerChunkCache chunkSource = level.getChunkSource();

        BiomeSource biomeSource = chunkSource.getGenerator().getBiomeSource();

        if (biomeSource == null) {
            throw error("BiomeSource == null.");
        }

        Climate.Sampler sampler = chunkSource.randomState().sampler();

        if (sampler == null) {
            throw error("Climate.Sampler == null.");
        }

        Set<ChunkPos> touchedChunks = new HashSet<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);

                // Перезаполняем биомы чанка из текущего BiomeSource.
                chunk.fillBiomesFromNoise(biomeSource, sampler);

                chunk.setUnsaved(true);
                touchedChunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }

        tryResendBiomes(level, touchedChunks);

        return touchedChunks.size();
    }

    private static Holder<Biome> getBiomeHolder(ServerLevel level, String biomeName) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocation.tryParse(biomeName);

        if (id == null) {
            throw error("Некорректный идентификатор биома: " + biomeName);
        }

        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);

        return registry.getHolder(key)
                .orElseThrow(() -> error("Биом не найден: " + id));
    }

    private static void tryResendBiomes(ServerLevel level, Set<ChunkPos> chunks) {
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            ChunkMap chunkMap = chunkSource.chunkMap;

            java.lang.reflect.Method method =
                    ChunkMap.class.getMethod("resendBiomesForChunk", LevelChunk.class);

            method.setAccessible(true);

            for (ChunkPos pos : chunks) {
                LevelChunk chunk = level.getChunk(pos.x, pos.z);
                method.invoke(chunkMap, chunk);
            }
        } catch (Throwable ignored) {
            // Если метода нет — не страшно.
            // Биомы обновятся после перезагрузки чанков или повторного входа.
        }
    }

    private static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}