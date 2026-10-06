package com.mazemap.client;

import com.mazemap.storage.PlayerMapData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class FragmentTextureCache {
    private static final int FRAG_SIZE = PlayerMapData.FRAGMENT_SIZE;
    private static final int TRANSPARENT = 0;
    // Цвета заданы в формате ABGR, который использует NativeImage.
    private static final int TREE_DARK = 0xFF17351F;
    private static final int TREE_DEEP = 0xFF214B2B;
    private static final int TREE_INK = 0xFF2F6638;
    private static final int TREE_LIGHT = 0xFF467D49;
    private static final int TREE_EDGE = 0xFF102918;
    private static final int CONIFER_DEEP = 0xFF263C1C;
    private static final int CONIFER_BASE = 0xFF385627;
    private static final int CONIFER_LIGHT = 0xFF4D6D35;
    private static final int BIRCH_DEEP = 0xFF315A35;
    private static final int BIRCH_BASE = 0xFF477849;
    private static final int BIRCH_LIGHT = 0xFF60945F;
    private static final int STRUCTURE_LIGHT = 0xFF92969A;
    private static final int STRUCTURE_INK = 0xFF74797E;
    private static final int STRUCTURE_DARK = 0xFF555B61;
    private static final int STRUCTURE_EDGE = 0xFF353B42;
    private static final int WATER_INK = 0xFF9A7448;
    private static final int CREATE_BRASS = 0xFF3B789D;

    private static final Map<Long, Entry> ENTRIES = new HashMap<>();

    private FragmentTextureCache() {}

    public static ResourceLocation getOrCreate(long key, byte[] pixels, byte[] heights) {
        Entry e = ENTRIES.get(key);
        if (e == null) {
            DynamicTexture tex = new DynamicTexture(FRAG_SIZE, FRAG_SIZE, false);
            tex.setFilter(false, false);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("mazemap", "frag_" + Long.toHexString(key));
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            e = new Entry(tex, loc);
            uploadPixels(tex, pixels, heights, key);
            ENTRIES.put(key, e);
            return loc;
        }
        if (e.dirty) {
            uploadPixels(e.texture, pixels, heights, key);
            e.dirty = false;
        }
        return e.location;
    }

    public static void invalidate(long key) {
        Entry e = ENTRIES.get(key);
        if (e != null) e.dirty = true;
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Entry e : ENTRIES.values()) {
            mc.getTextureManager().release(e.location);
            e.texture.close();
        }
        ENTRIES.clear();
    }

    private static void uploadPixels(DynamicTexture tex, byte[] pixels, byte[] heights, long fragmentKey) {
        NativeImage img = tex.getPixels();
        if (img == null) return;

        for (int y = 0; y < FRAG_SIZE; y++) {
            for (int x = 0; x < FRAG_SIZE; x++) {
                int unsigned = pixels[y * FRAG_SIZE + x] & 0xFF;
                int color;

                if (unsigned == 0 || unsigned == 1) {
                    // 0: Не исследовано
                    // 1: Проход (прозрачный)
                    color = TRANSPARENT;
                } else if (isTreePixel(unsigned)) {
                    color = treeCanopyColor(pixels, heights, x, y, unsigned);
                } else if (unsigned == (PlayerMapData.PIXEL_STRUCTURE & 0xFF)) {
                    color = structureStoneColor(pixels, x, y, fragmentKey, unsigned);
                } else if (unsigned == (PlayerMapData.PIXEL_WATER & 0xFF)) {
                    color = WATER_INK;
                } else if (unsigned == (PlayerMapData.PIXEL_CREATE & 0xFF)) {
                    // ABGR: тёплая латунь, чтобы механизмы Create читались на плане.
                    color = CREATE_BRASS;
                } else {
                    // 2..255: Стена с цветом и рельефом
                    // Ванильный метод getColorFromPackedId принимает (colorId << 2) | brightnessId
                    // и возвращает готовый ABGR int с правильным освещением!
                    color = shadeTerrainByHeight(
                            toBlueprintInk(MapColor.getColorFromPackedId(unsigned - 2)),
                            pixels, heights, x, y);
                }

                img.setPixelRGBA(x, y, color);
            }
        }
        tex.upload();
    }

    private static int treeCanopyColor(byte[] pixels, byte[] heights, int x, int y, int expected) {
        int deep = TREE_DEEP;
        int base = TREE_INK;
        int light = TREE_LIGHT;
        if (expected == (PlayerMapData.PIXEL_TREE_CONIFER & 0xFF)) {
            deep = CONIFER_DEEP;
            base = CONIFER_BASE;
            light = CONIFER_LIGHT;
        } else if (expected == (PlayerMapData.PIXEL_TREE_BIRCH & 0xFF)) {
            deep = BIRCH_DEEP;
            base = BIRCH_BASE;
            light = BIRCH_LIGHT;
        }
        if (heights == null || heights.length != PlayerMapData.HEIGHT_BYTES) return TREE_INK;

        int center = heights[y * FRAG_SIZE + x] & 0xFF;
        int sum = 0;
        int count = 0;
        int min = center;
        int max = center;
        boolean touchesGround = false;
        boolean touchesOtherSpecies = false;
        int[][] neighbours = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : neighbours) {
            int nx = x + d[0], ny = y + d[1];
            if (nx < 0 || ny < 0 || nx >= FRAG_SIZE || ny >= FRAG_SIZE) continue;
            int idx = ny * FRAG_SIZE + nx;
            int neighbourPixel = pixels[idx] & 0xFF;
            if (!isTreePixel(neighbourPixel)) {
                touchesGround = true;
                continue;
            }
            if (neighbourPixel != expected) touchesOtherSpecies = true;
            int neighbourHeight = heights[idx] & 0xFF;
            sum += neighbourHeight;
            count++;
            min = Math.min(min, neighbourHeight);
            max = Math.max(max, neighbourHeight);
        }
        // Одиночные пиксели листвы не должны превращаться в чёрные точки на карте.
        if (count < 2) return base;

        float average = sum / (float) count;
        // Граница вида и резкий перепад отделяют соседние деревья. Край кроны
        // затемняется мягко, без толстой чёрной пиксельной обводки.
        if (touchesOtherSpecies || max - min >= 5) return deep;
        int color = touchesGround ? deep : (center >= average + 1.0f ? light
                : (center <= average - 1.0f ? deep : base));
        return multiplyAbgr(color, hillshadeFactor(heights, x, y));
    }

    private static boolean isTreePixel(int value) {
        return value == (PlayerMapData.PIXEL_TREE & 0xFF)
                || value == (PlayerMapData.PIXEL_TREE_BIRCH & 0xFF)
                || value == (PlayerMapData.PIXEL_TREE_CONIFER & 0xFF);
    }

    private static int shadeTerrainByHeight(int abgr, byte[] pixels, byte[] heights, int x, int y) {
        if (heights == null || heights.length != PlayerMapData.HEIGHT_BYTES) return abgr;
        return multiplyAbgr(abgr, hillshadeFactor(heights, x, y));
    }

    /** Направленное освещение карты высот: свет идёт с северо-запада. */
    private static float hillshadeFactor(byte[] heights, int x, int y) {
        int center = heightAt(heights, x, y, 0);
        int west = heightAt(heights, x - 1, y, center);
        int east = heightAt(heights, x + 1, y, center);
        int north = heightAt(heights, x, y - 1, center);
        int south = heightAt(heights, x, y + 1, center);

        float gradientX = (west - east) * 0.055f;
        float gradientY = (south - north) * 0.045f;
        return Math.max(0.68f, Math.min(1.28f, 1.0f + gradientX + gradientY));
    }

    private static int heightAt(byte[] heights, int x, int y, int fallback) {
        if (x < 0 || y < 0 || x >= FRAG_SIZE || y >= FRAG_SIZE) return fallback;
        int value = heights[y * FRAG_SIZE + x] & 0xFF;
        return value == 0 ? fallback : value;
    }

    private static int multiplyAbgr(int abgr, float factor) {
        int a = (abgr >>> 24) & 0xFF;
        int b = clamp((int) (((abgr >>> 16) & 0xFF) * factor));
        int g = clamp((int) (((abgr >>> 8) & 0xFF) * factor));
        int r = clamp((int) ((abgr & 0xFF) * factor));
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int structureStoneColor(byte[] pixels, int x, int y, long fragmentKey, int expected) {
        if (touchesDifferentPixel(pixels, x, y, expected)) return STRUCTURE_EDGE;

        int globalX = PlayerMapData.unpackCellX(fragmentKey) * FRAG_SIZE + x;
        int globalY = PlayerMapData.unpackCellZ(fragmentKey) * FRAG_SIZE + y;
        int row = Math.floorDiv(globalY, 4);
        int shiftedX = globalX + ((row & 1) == 0 ? 0 : 3);

        // Неброская кладка: крупные серые камни и тонкие тёмные швы.
        if (Math.floorMod(globalY, 4) == 0 || Math.floorMod(shiftedX, 7) == 0) {
            return STRUCTURE_DARK;
        }
        int stone = mixHash(Math.floorDiv(shiftedX, 7), row);
        return Math.floorMod(stone, 5) == 0 ? STRUCTURE_LIGHT : STRUCTURE_INK;
    }

    private static int mixHash(int x, int y) {
        int h = x * 0x1f1f1f1f ^ y * 0x5f356495;
        h ^= h >>> 16;
        h *= 0x45d9f3b;
        return h ^ (h >>> 16);
    }

    private static boolean touchesDifferentPixel(byte[] pixels, int x, int y, int expected) {
        return x == 0 || y == 0 || x == FRAG_SIZE - 1 || y == FRAG_SIZE - 1
                || (pixels[y * FRAG_SIZE + x - 1] & 0xFF) != expected
                || (pixels[y * FRAG_SIZE + x + 1] & 0xFF) != expected
                || (pixels[(y - 1) * FRAG_SIZE + x] & 0xFF) != expected
                || (pixels[(y + 1) * FRAG_SIZE + x] & 0xFF) != expected;
    }

    /**
     * Переводит яркую ванильную палитру карты в приглушённую палитру
     * карандашного плана, сохраняя различимость травы, воды, камня и земли.
     * Цвет Minecraft здесь хранится в формате ABGR.
     */
    private static int toBlueprintInk(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int r = abgr & 0xFF;

        int luminance = (r * 30 + g * 59 + b * 11) / 100;
        // Сохраняем большую часть исходного оттенка, приглушая насыщенность как у чернил.
        int inkR = clamp((r * 62 + luminance * 28) / 100);
        int inkG = clamp((g * 62 + luminance * 26) / 100);
        int inkB = clamp((b * 62 + luminance * 20) / 100);
        return (a << 24) | (inkB << 16) | (inkG << 8) | inkR;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static final class Entry {
        final DynamicTexture texture;
        final ResourceLocation location;
        boolean dirty;
        Entry(DynamicTexture texture, ResourceLocation location) {
            this.texture = texture;
            this.location = location;
        }
    }
}
