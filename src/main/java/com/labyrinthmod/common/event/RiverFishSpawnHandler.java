package com.labyrinthmod.common.event;

import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps a small fish population in the generated river.
 *
 * Water mobs are not part of vanilla's chunk-generation creature pass, and adding
 * entities during terrain decoration is not reliable on every Forge server. This
 * server-side pass also populates river chunks that were generated before the fix.
 */
@Mod.EventBusSubscriber(modid = "labyrinthmod", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RiverFishSpawnHandler {
    private static final int CHECK_INTERVAL = 100;
    private static final int RIVER_RADIUS = 74;
    private static final int MAX_FISH = 22;
    private static final int WATER_LEVEL = 31;

    private RiverFishSpawnHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof LabyrinthChunkGenerator) || level.getGameTime() % CHECK_INTERVAL != 0) {
            return;
        }

        AABB riverArea = new AABB(
                -RIVER_RADIUS, level.getMinBuildHeight(), -RIVER_RADIUS,
                RIVER_RADIUS + 1, level.getMaxBuildHeight(), RIVER_RADIUS + 1);
        int currentFish = level.getEntitiesOfClass(AbstractFish.class, riverArea).size();
        if (currentFish >= MAX_FISH || level.players().isEmpty()) {
            return;
        }

        RandomSource random = level.random;
        int target = Math.min(2 + random.nextInt(3), MAX_FISH - currentFish);
        int spawned = 0;

        // Randomly sample the whole glade. Unloaded positions are skipped, so fish
        // appear only where a player has already brought the river into simulation.
        for (int attempt = 0; attempt < 96 && spawned < target; attempt++) {
            int x = random.nextInt(RIVER_RADIUS * 2 + 1) - RIVER_RADIUS;
            int z = random.nextInt(RIVER_RADIUS * 2 + 1) - RIVER_RADIUS;
            if (!LabyrinthChunkGenerator.isGeneratedRiverAt(x, z)) {
                continue;
            }

            BlockPos pos = findLoadedWater(level, x, z);
            if (pos == null || !level.noCollision(new AABB(pos).inflate(0.1D))) {
                continue;
            }

            EntityType<? extends Mob> type = random.nextFloat() < 0.65F
                    ? EntityType.SALMON
                    : EntityType.COD;
            Mob fish = type.create(level);
            if (fish == null) {
                continue;
            }

            fish.moveTo(pos.getX() + 0.5D, pos.getY() + 0.25D, pos.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            fish.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            if (level.addFreshEntity(fish)) {
                spawned++;
            }
        }
    }

    private static BlockPos findLoadedWater(ServerLevel level, int x, int z) {
        BlockPos top = new BlockPos(x, WATER_LEVEL, z);
        if (!level.hasChunkAt(top)) {
            return null;
        }

        for (int y = WATER_LEVEL - 1; y >= WATER_LEVEL - 6; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).is(Blocks.WATER)
                    && level.getBlockState(pos.above()).is(Blocks.WATER)) {
                return pos;
            }
        }
        return null;
    }
}
