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

    // ★ Кэш кривой реки ★
    private double[][] riverCurvePoints = null;

    public LabyrinthBiomeSource(BiomeSource baseSource, long seed) {
        this.baseSource = baseSource;
        this.seed = seed;
        System.out.println("[LabyrinthBiomeSource] Created with seed: " + seed);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        if (isForestBiomeAt(x, z)) {
            return getForestHolder();
        }
        if (isRiverBiomeAt(x, z)) {
            return getRiverHolder();
        }
        return baseSource.getNoiseBiome(x, y, z, sampler);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public Stream<Holder<Biome>> collectPossibleBiomes() {
        Holder<Biome> plains = getPlainsHolder();
        Holder<Biome> river = getRiverHolder();
        Holder<Biome> forest = getForestHolder();

        // ★ Защита от null: если реестр ещё не загружен ★
        if (plains == null || river == null || forest == null) {
            return Stream.empty();
        }

        return Stream.of(plains, river, forest);
    }

    // ==========================================
    // ★ ХОЛДЕРЫ БИОМОВ ★
    // ==========================================

    private Holder<Biome> getRiverHolder() {
        return ForgeRegistries.BIOMES.getHolder(Biomes.RIVER.location()).orElse(null);
    }

    private Holder<Biome> getPlainsHolder() {
        return ForgeRegistries.BIOMES.getHolder(Biomes.PLAINS.location()).orElse(null);
    }

    private Holder<Biome> getForestHolder() {
        return ForgeRegistries.BIOMES.getHolder(Biomes.FOREST.location()).orElse(null);
    }

    // ==========================================
    // ★ ЗОНА ЛЕСА: северная сторона, минимум 20 блоков от центра ★
    // ==========================================

    private boolean isForestBiomeAt(int x, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > 70) return false;
        if (z >= -30) return false;

        double distFromCenter = Math.sqrt((double) x * x + (double) z * z);
        if (distFromCenter < 20.0) return false;

        return true;
    }

    // ==========================================
    // ★ ЗОНА РЕКИ: расстояние до кривой Безье ★
    // ==========================================

    private boolean isRiverBiomeAt(int x, int z) {
        int dist = Math.max(Math.abs(x), Math.abs(z));
        if (dist > 70) return false;

        double riverDist = distanceToRiverCurve(x, z);

        // ★ УВЕЛИЧЕННАЯ ШИРИНА И БЕРЕГ ★
        double waterHalfWidth = 8.5;
        double bankHalfWidth = 3.0;

        return riverDist < waterHalfWidth + bankHalfWidth;
    }

    // ==========================================
    // ★ КРИВАЯ РЕКИ (Квадратичная Безье) ★
    // ==========================================

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

        // ★ Случайный начальный угол (вариативность) ★
        int startCorner = riverRand.nextInt(4);
        double startX, startZ, endX, endZ;

        switch (startCorner) {
            case 0: startX = -R; startZ = -R; break;
            case 1: startX = R; startZ = -R; break;
            case 2: startX = R; startZ = R; break;
            default: startX = -R; startZ = R; break;
        }

        // Противоположный угол
        int endCorner = (startCorner + 2) % 4;
        switch (endCorner) {
            case 0: endX = -R; endZ = -R; break;
            case 1: endX = R; endZ = -R; break;
            case 2: endX = R; endZ = R; break;
            default: endX = -R; endZ = R; break;
        }

        // ★ Смещение конечной точки на 15 блоков от входа ★
        double shiftDir = riverRand.nextBoolean() ? 1.0 : -1.0;
        if (endCorner == 0 || endCorner == 2) {
            endX += shiftDir * 15.0;
        } else {
            endZ += shiftDir * 15.0;
        }

        // ★ Контрольная точка: обход центра на 30 блоков ★
        double dirX = endX - startX;
        double dirZ = endZ - startZ;
        double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
        dirX /= dirLen;
        dirZ /= dirLen;

        // Перпендикуляр к диагонали
        double perpX = -dirZ;
        double perpZ = dirX;

        // Направление обхода (по часовой или против)
        double bypassDir = riverRand.nextBoolean() ? 1.0 : -1.0;
        double controlX = perpX * 30.0 * bypassDir;
        double controlZ = perpZ * 30.0 * bypassDir;

        // Генерация точек кривой Безье
        int pointCount = 50;
        riverCurvePoints = new double[pointCount][2];

        for (int i = 0; i < pointCount; i++) {
            double t = (double) i / (pointCount - 1);
            double mt = 1.0 - t;
            riverCurvePoints[i][0] = mt * mt * startX + 2 * mt * t * controlX + t * t * endX;
            riverCurvePoints[i][1] = mt * mt * startZ + 2 * mt * t * controlZ + t * t * endZ;
        }
    }
}