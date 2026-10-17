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
import com.labyrinthmod.LabyrinthMod;
import java.io.InputStream;
public class PersonalMapItemTexture {

    private static final int TEX_SIZE   = 128;                // размер текстуры предмета
    private static final int PX_PER_BLOCK = 2;                // = FRAGMENT_SIZE / FRAGMENT_SIZE_BLOCKS// = 64
    public static final int SHOWN_BLOCKS = TEX_SIZE / PX_PER_BLOCK;

    private static final Map<UUID, CachedTexture> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation BLANK_LOC =
            ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "textures/gui/personal_map_blank.png");
    private static final int FALLBACK_COLOR = 0xFFB0DCE8;
    private static NativeImage blank;
    private static boolean blankTried;

    private static NativeImage blank() {
        if (blank == null && !blankTried) {
            blankTried = true;
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(BLANK_LOC)) {
                NativeImage img = NativeImage.read(in);
                if (img.getWidth() == TEX_SIZE && img.getHeight() == TEX_SIZE) blank = img;
                else img.close();
            } catch (Exception ignored) {
            }
        }
        return blank;
    }

    private static void fillBackground(NativeImage dst) {
        NativeImage b = blank();
        if (b != null) dst.copyFrom(b);
        else dst.fillRect(0, 0, TEX_SIZE, TEX_SIZE, FALLBACK_COLOR);
    }

    private static final class CachedTexture {
        final ResourceLocation id;
        final DynamicTexture texture;
        long lastVersion = Long.MIN_VALUE;
        int  lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE;
        CachedTexture(UUID mapId) {
            this.id = ResourceLocation.fromNamespaceAndPath("mazemap",
                    "dynamic/map_item_" + mapId.toString().replace("-", ""));
            NativeImage img = new NativeImage(TEX_SIZE, TEX_SIZE, true);
            fillBackground(img);
            this.texture = new DynamicTexture(img);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }
    }

    public static ResourceLocation getTexture(UUID mapId) {
        return getView(mapId).texture;
    }

    private static void redraw(CachedTexture c, int centerX, int centerZ) {
        NativeImage dst = c.texture.getPixels();
        if (dst == null) return;
        fillBackground(dst);

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
        if (blank != null) {
            blank.close();
            blank = null;
        }
        blankTried = false;
        Minecraft mc = Minecraft.getInstance();
        for (CachedTexture ct : CACHE.values()) mc.getTextureManager().release(ct.id);
        CACHE.clear();
    }
    public static final class View {
        public final ResourceLocation texture;
        public final int worldLeft;
        public final int worldTop;

        private View(ResourceLocation texture, int worldLeft, int worldTop) {
            this.texture = texture;
            this.worldLeft = worldLeft;
            this.worldTop = worldTop;
        }
    }

    private static ResourceLocation whiteId;

    public static ResourceLocation getWhite() {
        if (whiteId == null) {
            NativeImage img = new NativeImage(1, 1, false);
            img.setPixelRGBA(0, 0, 0xFFFFFFFF);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mazemap", "dynamic/white");
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
            whiteId = id;
        }
        return whiteId;
    }

    public static View getView(UUID mapId) {
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
        return new View(c.id, c.lastCx - SHOWN_BLOCKS / 2, c.lastCz - SHOWN_BLOCKS / 2);
    }



}