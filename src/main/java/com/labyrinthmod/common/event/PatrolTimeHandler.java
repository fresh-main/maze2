package com.labyrinthmod.common.event;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.entity.GriverEntity;
import com.labyrinthmod.common.entity.GriverEntityType;
import com.labyrinthmod.common.generation.LabyrinthChunkGenerator;
import com.labyrinthmod.common.patrol.PatrolManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID)
public final class PatrolTimeHandler {
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int SPAWN_RADIUS = 25;
    private static final int DAWN_PLAYER_NOTICE_RADIUS = 64;
    private static final int MAX_WILD_GRIVERS = 6;
    private static final long NIGHT_START = 13000L;
    private static final long DAWN = 23000L;
    private static final Map<ResourceKey<Level>, Long> SPAWNED_NIGHT = new HashMap<>();
    private static final Map<ResourceKey<Level>, Long> RETREATED_NIGHT = new HashMap<>();

    private PatrolTimeHandler() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0) return;
        if (!(level.getChunkSource().getGenerator() instanceof LabyrinthChunkGenerator)) return;

        long absoluteDay = level.getDayTime() / 24000L;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean night = time >= NIGHT_START && time < DAWN;
        ResourceKey<Level> dimension = level.dimension();

        if (night) {
            RETREATED_NIGHT.remove(dimension);
            PatrolManager manager = PatrolManager.get(level);
            if (manager != null) manager.setGlobalPatrolActive(true);
            if (!Long.valueOf(absoluteDay).equals(SPAWNED_NIGHT.get(dimension))) {
                int spawned = spawnNightGrivers(level);
                if (spawned > 0) {
                    SPAWNED_NIGHT.put(dimension, absoluteDay);
                    LabyrinthMod.LOGGER.info("[GriverNight] spawned {} wild grivers for night {}", spawned, absoluteDay);
                }
            }
        } else {
            SPAWNED_NIGHT.remove(dimension);
            if (!Long.valueOf(absoluteDay).equals(RETREATED_NIGHT.get(dimension))) {
                int retreating = sendWildGriversToBurrow(level);
                PatrolManager manager = PatrolManager.get(level);
                if (manager != null) manager.setGlobalPatrolActive(false);
                RETREATED_NIGHT.put(dimension, absoluteDay);
                if (retreating > 0) {
                    LabyrinthMod.LOGGER.info("[GriverNight] {} wild grivers retreating at dawn", retreating);
                }
            }
        }
    }

    private static int spawnNightGrivers(ServerLevel level) {
        int remaining = MAX_WILD_GRIVERS - getWildGrivers(level).size();
        if (remaining <= 0) return 0;
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) return 0;

        RandomSource random = level.random;
        int desired = Math.min(remaining, Math.max(2, players.size() * 2));
        int spawned = 0;
        for (int i = 0; i < desired; i++) {
            ServerPlayer player = players.get(random.nextInt(players.size()));
            BlockPos spawn = findLoadedCorridor(level, random, player.blockPosition(), SPAWN_RADIUS, 160);
            if (spawn == null) spawn = findLoadedCorridor(level, random, null, 0, 320);
            if (spawn == null || tooCloseToAnotherGriver(level, spawn, 10.0D)) continue;

            List<BlockPos> route = createRoute(level, random, spawn);
            if (route.size() < 3) continue;

            GriverEntity griver = GriverEntityType.GRIVER.get().create(level);
            if (griver == null) continue;
            griver.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    random.nextFloat() * 360.0F, 0.0F);
            griver.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.EVENT, null, null);
            griver.setHomePos(spawn);
            griver.beginNaturalNightPatrol(route);
            if (level.addFreshEntity(griver)) spawned++;
        }
        return spawned;
    }

    private static List<BlockPos> createRoute(ServerLevel level, RandomSource random, BlockPos spawn) {
        int wanted = 4 + random.nextInt(4);
        List<BlockPos> route = new ArrayList<>(wanted);
        BlockPos center = spawn;
        int failures = 0;
        while (route.size() < wanted && failures < 40) {
            BlockPos point = findLoadedCorridor(level, random, center, SPAWN_RADIUS, 120);
            if (point == null || point.distSqr(center) < 36.0D || route.contains(point)) {
                failures++;
                continue;
            }
            route.add(point);
            center = point;
        }
        return route;
    }

    private static int sendWildGriversToBurrow(ServerLevel level) {
        int count = 0;
        for (GriverEntity griver : getWildGrivers(level)) {
            ServerPlayer nearbyPlayer = findNearestPlayer(level, griver.blockPosition(), DAWN_PLAYER_NOTICE_RADIUS);
            if (nearbyPlayer == null) {
                griver.beginImmediateBurrow();
            } else {
                BlockPos retreat = findDistantRetreat(level, level.random,
                        griver, nearbyPlayer.blockPosition());
                if (retreat == null) {
                    griver.beginImmediateBurrow();
                } else {
                    griver.beginDawnRetreat(retreat);
                }
            }
            count++;
        }
        return count;
    }

    private static ServerPlayer findNearestPlayer(ServerLevel level, BlockPos origin, int radius) {
        ServerPlayer nearest = null;
        double nearestSq = (double) radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || isProtectedFaction(player)) continue;
            double distanceSq = player.blockPosition().distSqr(origin);
            if (distanceSq <= nearestSq) {
                nearestSq = distanceSq;
                nearest = player;
            }
        }
        return nearest;
    }

    private static boolean isProtectedFaction(ServerPlayer player) {
        return player.getCapability(FractionProvider.FRACTION)
                .map(data -> data.getFraction() == FractionType.OPERATOR
                        || data.getFraction() == FractionType.IMPOSTER)
                .orElse(false);
    }

    private static BlockPos findDistantRetreat(ServerLevel level, RandomSource random,
                                                GriverEntity griver, BlockPos playerPos) {
        BlockPos from = griver.blockPosition();
        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        double awayX = from.getX() - playerPos.getX();
        double awayZ = from.getZ() - playerPos.getZ();
        double awayLength = Math.max(1.0D, Math.sqrt(awayX * awayX + awayZ * awayZ));
        awayX /= awayLength;
        awayZ /= awayLength;
        int pathChecks = 0;

        for (int i = 0; i < 480; i++) {
            BlockPos point = findLoadedCorridor(level, random, from, 96, 1);
            if (point == null || point.distSqr(from) < 900.0D) continue;
            double travelX = point.getX() - from.getX();
            double travelZ = point.getZ() - from.getZ();
            double travelLength = Math.max(1.0D, Math.sqrt(travelX * travelX + travelZ * travelZ));
            double awayAlignment = (travelX * awayX + travelZ * awayZ) / travelLength;
            if (awayAlignment < 0.15D) continue;
            double score = point.distSqr(playerPos) + point.distSqr(from) * 0.35D
                    + awayAlignment * 2500.0D;
            if (score > bestScore && pathChecks < 32) {
                pathChecks++;
                if (griver.canReachNaturalPoint(point)) {
                    bestScore = score;
                    best = point;
                }
            }
        }
        if (best != null) return best;

        // При малой дистанции прорисовки выбираем самый дальний загруженный проход,
        // который хотя бы не ведёт обратно к игроку.
        int fallbackPathChecks = 0;
        for (int i = 0; i < 320 && fallbackPathChecks < 32; i++) {
            BlockPos point = findLoadedCorridor(level, random, from, 64, 1);
            if (point == null) continue;
            double travelX = point.getX() - from.getX();
            double travelZ = point.getZ() - from.getZ();
            double awayDot = travelX * awayX + travelZ * awayZ;
            if (awayDot <= 0.0D) continue;
            if (best == null || point.distSqr(playerPos) > best.distSqr(playerPos)) {
                fallbackPathChecks++;
                if (griver.canReachNaturalPoint(point)) best = point;
            }
        }
        return best;
    }

    private static BlockPos findLoadedCorridor(ServerLevel level, RandomSource random,
                                               BlockPos center, int radius, int attempts) {
        for (int i = 0; i < attempts; i++) {
            BlockPos point = LabyrinthChunkGenerator.findRandomMainMazeCorridor(random, center, radius, 1);
            if (point != null && level.hasChunkAt(point) && canStand(level, point)) return point;
        }
        return null;
    }

    private static boolean canStand(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.above(2)).getCollisionShape(level, pos.above(2)).isEmpty();
    }

    private static boolean tooCloseToAnotherGriver(ServerLevel level, BlockPos pos, double radius) {
        return !level.getEntitiesOfClass(GriverEntity.class,
                new AABB(pos).inflate(radius), GriverEntity::isAlive).isEmpty();
    }

    private static List<GriverEntity> getWildGrivers(ServerLevel level) {
        List<GriverEntity> result = new ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof GriverEntity griver
                    && griver.isAlive() && griver.isNaturalNightSpawn()) result.add(griver);
        }
        return result;
    }
}
