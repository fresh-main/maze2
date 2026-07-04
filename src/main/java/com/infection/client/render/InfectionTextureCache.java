package com.infection.client.render;

import com.labyrinthmod.LabyrinthMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Процедурная маска-«прожилки» для наложения на модель игрока.
 *
 * Каждый пиксель 64x64 имеет порог появления 0..99. На уровне заражения L
 * отображаются только пиксели с порогом &lt; L — чем выше заражение, тем больше
 * чёрного покрытия. Дополнительно с каждым процентом появляется ровно 1% новых
 * пикселей (т.к. пороги распределены по оси).
 *
 * Генерация паттерна:
 *   1. «Прожилки» — случайные ходы от семян, с растущим вдоль хода порогом.
 *      Появляются первыми (небольшие штрихи на 1–15% заражения), постепенно
 *      удлиняются и ветвятся.
 *   2. Фоновый шум — заполняет пробелы на средних/высоких уровнях.
 *
 * Текстуры кэшируются лениво по уровню, всего до 101 штуки (1..100).
 */
public final class InfectionTextureCache {

    private static final int SIZE = 64;

    /**
     * Максимальный порог. При нормализации пороги распределяются по оси 0..MAX_THRESHOLD-1,
     * а пиксель отображается если threshold < level (0..100). То есть пиксели с порогом
     * >= 100 никогда не появляются, что оставляет (MAX - 100) / MAX процентов модели всегда скином.
     *
     * При MAX = 150 → на уровне 100% чёрных пикселей ~66%, остальные 34% остаются скином.
     */
    private static final int MAX_THRESHOLD = 150;

    private static final int[] THRESHOLDS = new int[SIZE * SIZE];
    private static final Map<Integer, ResourceLocation> CACHE = new ConcurrentHashMap<>();

    static {
        Random r = new Random(0xFEEDFACEL);

        // Изначально все пиксели "невидимы" (порог 100 — никогда не показываем).
        for (int i = 0; i < THRESHOLDS.length; i++) THRESHOLDS[i] = 100;

        // === 1. Прожилки (веиновые ходы) ===
        int walkCount = 55;
        for (int w = 0; w < walkCount; w++) {
            double fx = r.nextInt(SIZE);
            double fy = r.nextInt(SIZE);
            int len = 5 + r.nextInt(14);
            int startThreshold = r.nextInt(35);
            double dir = r.nextDouble() * Math.PI * 2;

            for (int step = 0; step < len; step++) {
                int px = (int) fx;
                int py = (int) fy;
                int t = Math.min(99, startThreshold + step * 3);

                // Жирность прожилки: 2x1 ядро + 1 «пиксель-ореол» для мягкого края.
                mark(px, py, t);
                mark(px + 1, py, t + 1);
                mark(px, py + 1, t + 1);
                mark(px + 1, py + 1, t + 2);
                mark(px - 1, py, t + 6);
                mark(px, py - 1, t + 6);

                fx += Math.cos(dir);
                fy += Math.sin(dir);
                dir += (r.nextDouble() - 0.5) * 0.9;
            }
        }

        // === 2. Фоновый шум — заполняет пробелы ===
        // Распределение с приоритетом: ~40% пикселей «средние» (30..70),
        // ~60% — «поздние» (70..99), чтобы модель темнела постепенно.
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (THRESHOLDS[i] >= 100) {
                THRESHOLDS[i] = r.nextInt(100) < 40
                        ? 30 + r.nextInt(40)
                        : 70 + r.nextInt(30);
            }
        }

        // === 3. Нормализация: распределяем пороги 0..MAX_THRESHOLD-1 по оси, ===
        // сохраняя исходный порядок (прожилки появляются первыми).
        Integer[] indices = new Integer[THRESHOLDS.length];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        java.util.Arrays.sort(indices, (a, b) -> Integer.compare(THRESHOLDS[a], THRESHOLDS[b]));
        int total = indices.length;
        for (int rank = 0; rank < total; rank++) {
            int idx = indices[rank];
            THRESHOLDS[idx] = (int) ((long) rank * MAX_THRESHOLD / total);
        }
    }

    private static void mark(int x, int y, int threshold) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) return;
        int idx = y * SIZE + x;
        int t = Math.min(99, threshold);
        if (t < THRESHOLDS[idx]) THRESHOLDS[idx] = t;
    }

    private InfectionTextureCache() {}

    public static ResourceLocation textureFor(int level) {
        int key = Math.max(1, Math.min(100, level));
        return CACHE.computeIfAbsent(key, InfectionTextureCache::build);
    }

    /** Сбрасывает кеш и освобождает зарегистрированные DynamicTexture.
     *  Вызывается при перезагрузке ресурсов (F3+T), чтобы старые текстуры не стакались. */
    public static void clearCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getTextureManager() != null) {
            for (ResourceLocation id : CACHE.values()) {
                mc.getTextureManager().release(id);
            }
        }
        CACHE.clear();
    }

    private static ResourceLocation build(int level) {
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int idx = y * SIZE + x;
                if (THRESHOLDS[idx] < level) {
                    img.setPixelRGBA(x, y, 0xFF000000); // непрозрачный чёрный
                } else {
                    img.setPixelRGBA(x, y, 0x00000000); // прозрачный
                }
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        ResourceLocation id = LabyrinthMod.id("dynamic/veins_" + level);
        Minecraft.getInstance().getTextureManager().register(id, tex);
        return id;
    }
}
