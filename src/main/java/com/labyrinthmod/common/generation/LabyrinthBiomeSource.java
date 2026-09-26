package com.labyrinthmod.common.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class LabyrinthBiomeSource extends BiomeSource {

    public static final Codec<LabyrinthBiomeSource> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    BiomeSource.CODEC.fieldOf("base_biome_source").forGetter(s -> s.baseSource),
                    Codec.LONG.optionalFieldOf("seed", 0L).forGetter(s -> s.seed),
                    Biome.CODEC.optionalFieldOf("river_biome").forGetter(s -> Optional.ofNullable(s.riverHolder)),
                    Biome.CODEC.optionalFieldOf("forest_biome").forGetter(s -> Optional.ofNullable(s.forestHolder)),
                    Biome.CODEC.optionalFieldOf("desert_biome").forGetter(s -> Optional.ofNullable(s.desertHolder))
            ).apply(inst, inst.stable(LabyrinthBiomeSource::new))
    );

    public static volatile LabyrinthBiomeSource INSTANCE;
    private final BiomeSource baseSource;
    private final long seed;

    // Кэшированные Holders, чтобы не дергать реестр Forge миллионы раз
    private Holder<Biome> riverHolder;
    private Holder<Biome> plainsHolder;
    private Holder<Biome> forestHolder;
    private Holder<Biome> desertHolder;

    private volatile double[][] riverCurvePoints = null;
    private volatile long riverSeedUsed = Long.MIN_VALUE;
    private volatile int riverRadiusUsed = -1;
    private final Object riverLock = new Object();

    private volatile ImprovedNoise forestNoise;
    private volatile ImprovedNoise forestDetailNoise;
    private volatile long forestNoiseSeed = Long.MIN_VALUE;
    private final Object forestNoiseLock = new Object();

    // ================= DEBUG COUNTERS =================
    private static final AtomicLong BIOME_CALLS = new AtomicLong(0);
    private static final AtomicLong INSIDE_GLADE_CALLS = new AtomicLong(0);
    private static final AtomicLong FOREST_ZONE_CALLS = new AtomicLong(0);
    private static final AtomicLong FOREST_NOISE_PASS = new AtomicLong(0);
    private static final AtomicLong FOREST_HITS = new AtomicLong(0);
    private static final AtomicLong RIVER_CONDITION_PASS = new AtomicLong(0);
    private static final AtomicLong RIVER_HITS = new AtomicLong(0);
    private static final AtomicLong BASE_HITS = new AtomicLong(0);
    private static volatile String lastResult = "none";
    private static volatile String lastPos = "none";
    private static volatile String lastInsidePos = "none";
    private static volatile boolean overrideActive = false;
    private static volatile int overrideMinX = 0;
    private static volatile int overrideMaxX = 0;
    private static volatile int overrideMinZ = 0;
    private static volatile int overrideMaxZ = 0;
    private static volatile Holder<Biome> overrideBiome = null;
    // ===================================================

    public LabyrinthBiomeSource(BiomeSource baseSource, long seed) {
        this(baseSource, seed, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public LabyrinthBiomeSource(BiomeSource baseSource, long seed,
                                Holder<Biome> river, Holder<Biome> forest, Holder<Biome> desert) {
        this(baseSource, seed, Optional.of(river), Optional.of(forest), Optional.of(desert));
    }

    private LabyrinthBiomeSource(BiomeSource baseSource, long seed,
                                 Optional<Holder<Biome>> river,
                                 Optional<Holder<Biome>> forest,
                                 Optional<Holder<Biome>> desert) {
        INSTANCE = this;
        this.baseSource = baseSource;
        this.seed = seed;
        this.riverHolder = river.orElse(null);
        this.forestHolder = forest.orElse(null);
        this.desertHolder = desert.orElse(null);
        System.out.println("[LabyrinthBiomeSource] Created with seed: " + seed);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // В 1.18+ сюда приходят координаты, поделённые на 4. Возвращаем их к реальным блокам.
        int realX = x << 2;
        int realZ = z << 2;

        Holder<Biome> override = getOverrideBiome(realX, realZ);
        if (override != null) {
            return override;
        }

        BIOME_CALLS.incrementAndGet();
        int radius = getGladeRadius();
        int dist = Math.max(Math.abs(realX), Math.abs(realZ));

        if (dist <= radius) {
            INSIDE_GLADE_CALLS.incrementAndGet();
            lastInsidePos = realX + "," + realZ;
        }

        // ВАЖНО: река должна проверяться раньше леса.
        boolean hasGenerator = LabyrinthChunkGenerator.hasActiveGenerator();
        boolean riverAtPosition = hasGenerator
                ? LabyrinthChunkGenerator.isGeneratedRiverAt(realX, realZ)
                : isRiverBiomeAt(realX, realZ, radius);
        if (riverAtPosition) {
            RIVER_CONDITION_PASS.incrementAndGet();
            Holder<Biome> river = getRiverHolder();
            if (river != null) {
                RIVER_HITS.incrementAndGet();
                lastResult = "river";
                lastPos = realX + "," + realZ;
                return river;
            }
        }

        boolean forestAtPosition = hasGenerator
                ? LabyrinthChunkGenerator.isGeneratedForestAt(realX, realZ)
                : isForestBiomeAt(realX, realZ, radius);
        if (forestAtPosition) {
            FOREST_NOISE_PASS.incrementAndGet();
            Holder<Biome> forest = getForestHolder();
            if (forest != null) {
                FOREST_HITS.incrementAndGet();
                lastResult = "forest";
                lastPos = realX + "," + realZ;
                return forest;
            }
        }

        if (dist > getSectorsEnd()) {
            Holder<Biome> desert = getDesertHolder();
            if (desert != null) {
                lastResult = "desert";
                lastPos = realX + "," + realZ;
                return desert;
            }
        }

        BASE_HITS.incrementAndGet();
        lastResult = "base";
        lastPos = realX + "," + realZ;
        return baseSource.getNoiseBiome(x, y, z, sampler);
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(
                // possibleBiomes() возвращает Set, поэтому вызываем .stream()
                baseSource.possibleBiomes().stream(),
                Stream.of(getPlainsHolder(), getRiverHolder(), getForestHolder(), getDesertHolder()).filter(Objects::nonNull)
        ).distinct();
    }

    public static String getDebugSummary() {
        return "LabyrinthBiomeSource:" +
                " calls=" + BIOME_CALLS.get() +
                ", insideGlade=" + INSIDE_GLADE_CALLS.get() +
                ", forestZone=" + FOREST_ZONE_CALLS.get() +
                ", forestNoisePass=" + FOREST_NOISE_PASS.get() +
                ", forest=" + FOREST_HITS.get() +
                ", riverConditionPass=" + RIVER_CONDITION_PASS.get() +
                ", river=" + RIVER_HITS.get() +
                ", base=" + BASE_HITS.get() +
                ", radius=" + (INSTANCE != null ? INSTANCE.getGladeRadius() : "?") +
                ", last=" + lastResult +
                ", lastPos=" + lastPos +
                ", lastInsidePos=" + lastInsidePos;
    }

    public String debugTestAt(int x, int z) {
        int radius = getGladeRadius();
        int dist = Math.max(Math.abs(x), Math.abs(z));
        double center = Math.sqrt((double) x * x + (double) z * z);
        int zLimit = getForestZLimit(radius);
        double centerExclusion = getCenterExclusion(radius);
        boolean forestMask = dist <= radius && z < zLimit && center >= centerExclusion;
        boolean forest = isForestBiomeAt(x, z, radius);
        boolean river = isRiverBiomeAt(x, z, radius);
        return "x=" + x +
                ", z=" + z +
                ", radius=" + radius +
                ", dist=" + dist +
                ", center=" + String.format("%.2f", center) +
                ", zLimit=" + zLimit +
                ", centerExclusion=" + String.format("%.2f", centerExclusion) +
                ", forestMask=" + forestMask +
                ", forest=" + forest +
                ", river=" + river;
    }

    // ================= BIOME HOLDERS (CACHED) =================
    private Holder<Biome> getRiverHolder() {
        if (riverHolder == null) {
            riverHolder = ForgeRegistries.BIOMES.getHolder(Biomes.RIVER.location()).orElse(null);
        }
        return riverHolder;
    }

    private Holder<Biome> getPlainsHolder() {
        if (plainsHolder == null) {
            plainsHolder = ForgeRegistries.BIOMES.getHolder(Biomes.PLAINS.location()).orElse(null);
        }
        return plainsHolder;
    }

    private Holder<Biome> getForestHolder() {
        if (forestHolder == null) {
            forestHolder = ForgeRegistries.BIOMES.getHolder(Biomes.FOREST.location()).orElse(null);
        }
        return forestHolder;
    }

    private Holder<Biome> getDesertHolder() {
        if (desertHolder == null) {
            desertHolder = ForgeRegistries.BIOMES.getHolder(Biomes.DESERT.location()).orElse(null);
        }
        return desertHolder;
    }
    // ==========================================================

    private int getGladeRadius() {
        try {
            LabyrinthConfig cfg = LabyrinthConfig.getInstance();
            if (cfg != null) {
                int r = cfg.gleydRadius;
                if (r > 10) {
                    return r;
                }
            }
        } catch (Throwable ignored) {
        }
        return 70;
    }

    private int getSectorsEnd() {
        LabyrinthConfig cfg = LabyrinthConfig.getInstance();
        int gladeRadius = cfg != null ? cfg.gleydRadius : 70;
        int mainMazeWidth = (cfg != null ? cfg.mainMazeWidth : 10) * 10;
        int sectorWidth = (cfg != null ? cfg.sectorWidth : 6) * 12;
        return gladeRadius + mainMazeWidth + 7 + sectorWidth;
    }

    private int getForestZLimit(int radius) {
        if (radius <= 70) {
            return -30;
        }
        return -Math.max(30, radius * 43 / 100);
    }

    private double getCenterExclusion(int radius) {
        return Math.max(12.0, radius * 2.0 / 7.0);
    }

    // =========================================================
    // FOREST
    // =========================================================
    private boolean isForestBiomeAt(int x, int z, int radius) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > radius) {
            return false;
        }
        int zLimit = getForestZLimit(radius);
        // ВНИМАНИЕ: Лес спавнится ТОЛЬКО если z < zLimit (например, z < -30).
        // Если вы спавнитесь в 0,0 и смотрите на юг (z > 0), леса не будет!
        if (z >= zLimit) {
            return false;
        }
        double centerExclusion = getCenterExclusion(radius);
        double distFromCenter = Math.sqrt((double) x * x + (double) z * z);
        if (distFromCenter < centerExclusion) {
            return false;
        }

        FOREST_ZONE_CALLS.incrementAndGet();
        ensureForestNoise();
        ImprovedNoise large = forestNoise;
        ImprovedNoise detail = forestDetailNoise;
        if (large == null || detail == null) {
            return true;
        }

        double largeNoise = large.noise(x * 0.0075, 0, z * 0.0075);
        double detailNoise = detail.noise(x * 0.035, 0, z * 0.035);

        double edgeStart = radius * 0.75;
        double edgeWidth = Math.max(1.0, radius - edgeStart);
        double radialFade = 1.0 - Math.max(0.0, (dist - edgeStart) / edgeWidth);
        radialFade = Math.max(0.0, Math.min(1.0, radialFade));

        double northStart = Math.abs(zLimit);
        double northDepth = Math.max(8.0, radius * 0.12);
        double northFade = Math.min(1.0, Math.max(0.0, (-z - northStart) / northDepth));

        double value = largeNoise * 0.50
                + detailNoise * 0.25
                + northFade * 0.30
                + radialFade * 0.20;

        return value > -0.20;
    }

    private long getEffectiveSeed() {
        if (WorldSeedHolder.isSeedLoaded()) {
            long s = WorldSeedHolder.getWorldSeed();
            if (s != 0) {
                return s;
            }
        }
        if (this.seed != 0) {
            return this.seed;
        }
        return 0x9E3779B97F4A7C15L;
    }

    private void ensureForestNoise() {
        long currentSeed = getEffectiveSeed();
        if (forestNoiseSeed == currentSeed) {
            return;
        }
        synchronized (forestNoiseLock) {
            if (forestNoiseSeed == currentSeed) {
                return;
            }
            ImprovedNoise large = new ImprovedNoise(new LegacyRandomSource(currentSeed ^ 0xF0E57L));
            ImprovedNoise detail = new ImprovedNoise(new LegacyRandomSource(currentSeed ^ 0xDE7A11L));
            forestNoise = large;
            forestDetailNoise = detail;
            forestNoiseSeed = currentSeed;
        }
    }

    // =========================================================
    // RIVER
    // =========================================================
    private boolean isRiverBiomeAt(int x, int z, int radius) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > radius) {
            return false;
        }
        double riverDist = distanceToRiverCurve(x, z, radius);
        double waterHalfWidth = 8.5;
        double bankHalfWidth = 3.5;
        return riverDist < waterHalfWidth + bankHalfWidth;
    }

    private double distanceToRiverCurve(double x, double z, int radius) {
        ensureRiverCurve(radius);
        double[][] points = riverCurvePoints;
        if (points == null) {
            return Double.MAX_VALUE;
        }
        double minDistSq = Double.MAX_VALUE;
        for (int i = 0; i < points.length - 1; i++) {
            double x1 = points[i][0];
            double z1 = points[i][1];
            double x2 = points[i + 1][0];
            double z2 = points[i + 1][1];
            double dx = x2 - x1;
            double dz = z2 - z1;
            double lenSq = dx * dx + dz * dz;
            double t = 0.0;
            if (lenSq > 0.0001) {
                t = ((x - x1) * dx + (z - z1) * dz) / lenSq;
                t = Math.max(0.0, Math.min(1.0, t));
            }
            double projX = x1 + t * dx;
            double projZ = z1 + t * dz;
            double distSq = (x - projX) * (x - projX) + (z - projZ) * (z - projZ);
            if (distSq < minDistSq) {
                minDistSq = distSq;
            }
        }
        return Math.sqrt(minDistSq);
    }

    private void ensureRiverCurve(int radius) {
        long currentSeed = getEffectiveSeed();
        double[][] existing = riverCurvePoints;
        if (existing != null && riverSeedUsed == currentSeed && riverRadiusUsed == radius) {
            return;
        }
        synchronized (riverLock) {
            existing = riverCurvePoints;
            if (existing != null && riverSeedUsed == currentSeed && riverRadiusUsed == radius) {
                return;
            }
            long riverSeed = currentSeed ^ 0x5EEDL;
            Random riverRand = new Random(riverSeed);
            int R = radius;
            int startCorner = riverRand.nextInt(4);
            double startX, startZ, endX, endZ;

            switch (startCorner) {
                case 0: startX = -R + 2; startZ = -R + 2; break;
                case 1: startX = R - 2; startZ = -R + 2; break;
                case 2: startX = R - 2; startZ = R - 2; break;
                default: startX = -R + 2; startZ = R - 2; break;
            }

            int endCorner = (startCorner + 2) % 4;
            switch (endCorner) {
                case 0: endX = -R; endZ = -R; break;
                case 1: endX = R; endZ = -R; break;
                case 2: endX = R; endZ = R; break;
                default: endX = -R; endZ = R; break;
            }

            double shiftDir = riverRand.nextBoolean() ? 1.0 : -1.0;
            double shift = Math.max(8.0, R * 0.20);
            if (endCorner == 0 || endCorner == 2) {
                endX += shiftDir * shift;
            } else {
                endZ += shiftDir * shift;
            }

            endX = Math.max(-R + 3, Math.min(R - 3, endX));
            endZ = Math.max(-R + 3, Math.min(R - 3, endZ));

            int pointCount = Math.max(60, Math.min(160, R));
            double[][] points = new double[pointCount][2];
            double currentX = startX;
            double currentZ = startZ;
            double centerAvoidRadius = Math.max(18.0, R * 0.50);
            double wallMargin = Math.max(6.0, R * 0.10);
            double maxStep = Math.max(2.0, R / 25.0);

            for (int i = 0; i < pointCount; i++) {
                points[i][0] = currentX;
                points[i][1] = currentZ;
                if (i == pointCount - 1) break;

                double attractX = endX - currentX;
                double attractZ = endZ - currentZ;
                double attractDist = Math.sqrt(attractX * attractX + attractZ * attractZ);
                if (attractDist > 0.001) {
                    attractX /= attractDist;
                    attractZ /= attractDist;
                }

                double repelCenterX = 0;
                double repelCenterZ = 0;
                double centerDist = Math.sqrt(currentX * currentX + currentZ * currentZ);
                if (centerDist < centerAvoidRadius && centerDist > 0.001) {
                    double t = 1.0 - centerDist / centerAvoidRadius;
                    double strength = t * t * 6.0;
                    repelCenterX = (currentX / centerDist) * strength;
                    repelCenterZ = (currentZ / centerDist) * strength;
                }

                double repelWallX = 0;
                double repelWallZ = 0;
                if (attractDist > R * 0.15) {
                    double distRight = R - currentX;
                    double distLeft = currentX + R;
                    double distUp = R - currentZ;
                    double distDown = currentZ + R;
                    double minWallDist = Math.min(Math.min(distRight, distLeft), Math.min(distUp, distDown));
                    if (minWallDist < wallMargin) {
                        double strength = (1.0 - minWallDist / wallMargin) * 2.5;
                        if (minWallDist == distRight) repelWallX = -strength;
                        else if (minWallDist == distLeft) repelWallX = strength;
                        else if (minWallDist == distUp) repelWallZ = -strength;
                        else repelWallZ = strength;
                    }
                }

                double totalX = attractX * 1.5 + repelCenterX + repelWallX;
                double totalZ = attractZ * 1.5 + repelCenterZ + repelWallZ;
                double totalDist = Math.sqrt(totalX * totalX + totalZ * totalZ);
                if (totalDist > 0.001) {
                    totalX /= totalDist;
                    totalZ /= totalDist;
                }

                double stepSize = attractDist / (pointCount - 1 - i);
                stepSize = Math.min(stepSize, attractDist);
                stepSize = Math.min(stepSize, maxStep);

                currentX += totalX * stepSize;
                currentZ += totalZ * stepSize;
                currentX = Math.max(-R + 3, Math.min(R - 3, currentX));
                currentZ = Math.max(-R + 3, Math.min(R - 3, currentZ));
            }

            points[pointCount - 1][0] = endX;
            points[pointCount - 1][1] = endZ;
            riverCurvePoints = points;
            riverSeedUsed = currentSeed;
            riverRadiusUsed = radius;
        }
    }

    // ================= OVERRIDE LOGIC =================
    public static void setOverride(BlockPos from, BlockPos to, Holder<Biome> biome) {
        overrideMinX = Math.min(from.getX(), to.getX());
        overrideMaxX = Math.max(from.getX(), to.getX());
        overrideMinZ = Math.min(from.getZ(), to.getZ());
        overrideMaxZ = Math.max(from.getZ(), to.getZ());
        overrideBiome = biome;
        overrideActive = true;
    }

    public static void clearOverride() {
        overrideActive = false;
        overrideBiome = null;
    }

    private static Holder<Biome> getOverrideBiome(int x, int z) {
        if (!overrideActive) return null;
        Holder<Biome> biome = overrideBiome;
        if (biome == null) return null;
        if (x >= overrideMinX && x <= overrideMaxX && z >= overrideMinZ && z <= overrideMaxZ) {
            return biome;
        }
        return null;
    }
}
