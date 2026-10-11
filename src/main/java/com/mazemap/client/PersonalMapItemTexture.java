package com.mazemap.client;

import com.mazemap.storage.PlayerMapData;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Текстура карты для предмета. Источник пикселей — FragmentTextureCache,
 * т.е. ровно то, что рисует PersonalMapScreen. Ничего своего не считаем.
 *
 * Масштаб:
 *   фрагмент 32 блока → 64 px (FragmentTextureCache), т.е. 2 px/block.
 *   наша текстура: 128 px на 64 блока мира → тоже 2 px/block.
 *   Значит просто копируем 64×64 из каждой видимой текстуры в нужное место.
 */
public class PersonalMapItemTexture {

    private static final int TEX_SIZE   = 128;                // размер текстуры предмета
    private static final int PX_PER_BLOCK = 2;                // = FRAGMENT_SIZE / FRAGMENT_SIZE_BLOCKS
    private static final int SHOWN_BLOCKS = TEX_SIZE / PX_PER_BLOCK; // = 64

    private static final Map<UUID, CachedTexture> CACHE = new ConcurrentHashMap<>();

    private static final class CachedTexture {
        final ResourceLocation id;
        final DynamicTexture texture;
        long lastVersion = Long.MIN_VALUE;
        int  lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        CachedTexture(UUID mapId) {
            this.id = ResourceLocation.fromNamespaceAndPath("mazemap",
                    "dynamic/map_item_" + mapId.toString().replace("-", ""));
            NativeImage img = new NativeImage(TEX_SIZE, TEX_SIZE, true);
            img.fillRect(0, 0, TEX_SIZE, TEX_SIZE, 0xFFE8DCB0);
            this.texture = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    public static ResourceLocation getTexture(UUID mapId) {
        CachedTexture c = CACHE.computeIfAbsent(mapId, CachedTexture::new);
        Minecraft mc = Minecraft.getInstance();

        int cx = (mc.player != null) ? (int) Math.floor(mc.player.getX()) : 0;
        int cz = (mc.player != null) ? (int) Math.floor(mc.player.getZ()) : 0;
        long version = ClientMazeMapHandlers.getDataVersion();

        if (version != c.lastVersion || cx != c.lastCx || cz != c.lastCz) {
            redraw(c, cx, cz);
            c.lastVersion = version;
            c.lastCx = cx;
            c.lastCz = cz;
        }
        return c.id;
    }

    private static void redraw(CachedTexture c, int centerX, int centerZ) {
        NativeImage dst = c.texture.getPixels();
        if (dst == null) return;
        dst.fillRect(0, 0, TEX_SIZE, TEX_SIZE, 0xFFE8DCB0);

        int fragSize   = PlayerMapData.FRAGMENT_SIZE;         // 64
        int fragBlocks = PlayerMapData.FRAGMENT_SIZE_BLOCKS;  // 32

        int worldLeft = centerX - SHOWN_BLOCKS / 2;
        int worldTop  = centerZ - SHOWN_BLOCKS / 2;

        int cellMinX = Math.floorDiv(worldLeft, fragBlocks);
        int cellMaxX = Math.floorDiv(worldLeft + SHOWN_BLOCKS - 1, fragBlocks);
        int cellMinZ = Math.floorDiv(worldTop, fragBlocks);
        int cellMaxZ = Math.floorDiv(worldTop + SHOWN_BLOCKS - 1, fragBlocks);

        Map<Long, PlayerMapData.Fragment> frags = ClientMazeMapHandlers.getFragments();
        var texMgr = Minecraft.getInstance().getTextureManager();

        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                PlayerMapData.Fragment frag = frags.get(key);
                if (frag == null || frag.pixels == null) continue;

                // та же самая текстура, что рисуется в PersonalMapScreen
                ResourceLocation loc = FragmentTextureCache.getOrCreate(key, frag.pixels, frag.heights);
                AbstractTexture at = texMgr.getTexture(loc);
                if (!(at instanceof DynamicTexture fragTex)) continue;
                NativeImage src = fragTex.getPixels();
                if (src == null) continue;

                int fragOriginX = cx * fragBlocks;
                int fragOriginZ = cz * fragBlocks;

                int baseX = (fragOriginX - worldLeft) * PX_PER_BLOCK;
                int baseY = (fragOriginZ - worldTop)  * PX_PER_BLOCK;

                for (int sy = 0; sy < fragSize; sy++) {
                    int dy = baseY + sy;
                    if (dy < 0 || dy >= TEX_SIZE) continue;
                    for (int sx = 0; sx < fragSize; sx++) {
                        int dx = baseX + sx;
                        if (dx < 0 || dx >= TEX_SIZE) continue;
                        int px = src.getPixelRGBA(sx, sy);
                        if ((px & 0xFF000000) == 0) continue; // прозрачный — бумага видна
                        dst.setPixelRGBA(dx, dy, px);
                    }
                }
            }
        }
        c.texture.upload();
    }

    public static void clearCache() {
        Minecraft mc = Minecraft.getInstance();
        for (CachedTexture ct : CACHE.values()) mc.getTextureManager().release(ct.id);
        CACHE.clear();
    }
}