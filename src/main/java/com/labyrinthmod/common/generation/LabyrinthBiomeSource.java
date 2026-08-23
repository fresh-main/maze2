package com.labyrinthmod.common.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;
import java.util.stream.Stream;

public class LabyrinthBiomeSource extends BiomeSource {

    public static final Codec<LabyrinthBiomeSource> CODEC = RecordCodecBuilder.create(inst ->
            inst.group(
                    BiomeSource.CODEC.fieldOf("base_biome_source").forGetter(s -> s.baseSource),
                    Codec.LONG.fieldOf("seed").forGetter(s -> s.seed)
            ).apply(inst, inst.stable(LabyrinthBiomeSource::new))
    );

    private final BiomeSource baseSource;
    private final long seed;
    private double[][] riverCurvePoints = null;
    private static boolean debugPrinted = false; // для однократного вывода отладки

    public LabyrinthBiomeSource(BiomeSource baseSource, long seed) {
        this.baseSource = baseSource;
        this.seed = seed;
        System.out.println("[LabyrinthBiomeSource] Created with seed: " + seed);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // ★★★ КРИТИЧЕСКИ ВАЖНО: координаты в getNoiseBiome ПОДЕЛЕНЫ НА 4 ★★★
        int realX = x << 2; // то же что x * 4
        int realZ = z << 2;

        // Отладка: выводим первые несколько вызовов
        if (!debugPrinted) {
            debugPrinted = true;
            System.out.println("[BiomeDebug] getNoiseBiome called: raw=(" + x + "," + y + "," + z
                    + ") real=(" + realX + "," + realZ + ")");
        }

        if (isForestBiomeAt(realX, realZ)) {
            Holder<Biome> forest = getForestHolder();
            if (forest != null) return forest;
        }
        if (isRiverBiomeAt(realX, realZ)) {
            Holder<Biome> river = getRiverHolder();
            if (river != null) return river;
        }
        return baseSource.getNoiseBiome(x, y, z, sampler);
    }

    @Override
    public Stream<Holder<Biome>> collectPossibleBiomes() {
        Holder<Biome> plains = getPlainsHolder();
        Holder<Biome> river = getRiverHolder();
        Holder<Biome> forest = getForestHolder();

        if (plains == null || river == null || forest == null) {
            System.err.println("[LabyrinthBiomeSource] WARNING: Some biome holders are null! "
                    + "plains=" + plains + " river=" + river + " forest=" + forest);
            return Stream.empty();
        }
        return Stream.of(plains, river, forest);
    }

    private Holder<Biome> getRiverHolder() {
        return ForgeRegistries.BIOMES.getHolder(Biomes.RIVER.location()).orElse(null);
    }

    private Holder<Biome> getPlainsHolder() {
        return ForgeRegistries.BIOMES.getHolder(Biomes.PLAINS.location()).orElse(null);
    }

    private Holder<Biome> getForestHolder() {
        return ForgeRegistries.BIOMES.getHolder(Biomes.FOREST.location()).orElse(null);
    }

    // ★ ЗОНА ЛЕСА: принимает РЕАЛЬНЫЕ координаты ★
    private boolean isForestBiomeAt(int x, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > 70) return false;
        if (z >= -30) return false;

        double distFromCenter = Math.sqrt((double) x * x + (double) z * z);
        if (distFromCenter < 20.0) return false;

        return true;
    }

    // ★ ЗОНА РЕКИ: принимает РЕАЛЬНЫЕ координаты ★
    private boolean isRiverBiomeAt(int x, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > 70) return false;

        double riverDist = distanceToRiverCurve(x, z);

        double waterHalfWidth = 8.5;
        double bankHalfWidth = 3.0;
        return riverDist < waterHalfWidth + bankHalfWidth;
    }

    private double distanceToRiverCurve(double x, double z) {
        ensureRiverCurve();
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < riverCurvePoints.length; i++) {
            double dx = x - riverCurvePoints[i][0];
            double dz = z - riverCurvePoints[i][1];
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist;
    }

    private void ensureRiverCurve() {
        if (riverCurvePoints != null) return;

        long riverSeed = this.seed ^ 0x5EEDL;
        Random riverRand = new Random(riverSeed);
        int R = 70;

        int startCorner = riverRand.nextInt(4);
        double startX, startZ, endX, endZ;
        switch (startCorner) {
            case 0: startX = -R; startZ = -R; break;
            case 1: startX = R; startZ = -R; break;
            case 2: startX = R; startZ = R; break;
            default: startX = -R; startZ = R; break;
        }

        int endCorner = (startCorner + 2) % 4;
        switch (endCorner) {
            case 0: endX = -R; endZ = -R; break;
            case 1: endX = R; endZ = -R; break;
            case 2: endX = R; endZ = R; break;
            default: endX = -R; endZ = R; break;
        }

        double shiftDir = riverRand.nextBoolean() ? 1.0 : -1.0;
        if (endCorner == 0 || endCorner == 2) {
            endX += shiftDir * 15.0;
        } else {
            endZ += shiftDir * 15.0;
        }

        int pointCount = 60;
        riverCurvePoints = new double[pointCount][2];

        double currentX = startX;
        double currentZ = startZ;

        double liftX = 0.0;
        double liftZ = 0.0;
        double liftAvoidRadius = 40.0;
        double centerAvoidRadius = 40.0;
        double wallMargin = 8.0;

        for (int i = 0; i < pointCount; i++) {
            riverCurvePoints[i][0] = currentX;
            riverCurvePoints[i][1] = currentZ;

            if (i == pointCount - 1) break;

            double attractX = endX - currentX;
            double attractZ = endZ - currentZ;
            double attractDist = Math.sqrt(attractX * attractX + attractZ * attractZ);
            if (attractDist > 0.001) {
                attractX /= attractDist;
                attractZ /= attractDist;
            }

            double repelLiftX = 0, repelLiftZ = 0;
            double dlx = currentX - liftX;
            double dlz = currentZ - liftZ;
            double liftDist = Math.sqrt(dlx * dlx + dlz * dlz);
            if (liftDist < liftAvoidRadius && liftDist > 0.001) {
                double t = 1.0 - liftDist / liftAvoidRadius;
                double strength = t * t * 4.0;
                repelLiftX = (dlx / liftDist) * strength;
                repelLiftZ = (dlz / liftDist) * strength;
            }

            double repelCenterX = 0, repelCenterZ = 0;
            double centerDist = Math.sqrt(currentX * currentX + currentZ * currentZ);
            if (centerDist < centerAvoidRadius && centerDist > 0.001) {
                double t = 1.0 - centerDist / centerAvoidRadius;
                double strength = t * t * 3.0;
                repelCenterX = (currentX / centerDist) * strength;
                repelCenterZ = (currentZ / centerDist) * strength;
            }

            double repelWallX = 0, repelWallZ = 0;
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

            double totalX = attractX + repelLiftX + repelCenterX + repelWallX;
            double totalZ = attractZ + repelLiftZ + repelCenterZ + repelWallZ;

            double totalDist = Math.sqrt(totalX * totalX + totalZ * totalZ);
            if (totalDist > 0.001) {
                totalX /= totalDist;
                totalZ /= totalDist;
            }

            double stepSize = attractDist / (pointCount - 1 - i);
            stepSize = Math.min(stepSize, attractDist);

            currentX += totalX * stepSize;
            currentZ += totalZ * stepSize;

            currentX = Math.max(-R + 3, Math.min(R - 3, currentX));
            currentZ = Math.max(-R + 3, Math.min(R - 3, currentZ));
        }

        riverCurvePoints[pointCount - 1][0] = endX;
        riverCurvePoints[pointCount - 1][1] = endZ;
    }
}