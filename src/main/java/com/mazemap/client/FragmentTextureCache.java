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

    private static final Map<Long, Entry> ENTRIES = new HashMap<>();

    private FragmentTextureCache() {}

    public static ResourceLocation getOrCreate(long key, byte[] pixels) {
        Entry e = ENTRIES.get(key);
        if (e == null) {
            DynamicTexture tex = new DynamicTexture(FRAG_SIZE, FRAG_SIZE, false);
            tex.setFilter(false, false);
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("mazemap", "frag_" + Long.toHexString(key));
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            e = new Entry(tex, loc);
            uploadPixels(tex, pixels);
            ENTRIES.put(key, e);
            return loc;
        }
        if (e.dirty) {
            uploadPixels(e.texture, pixels);
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

    private static void uploadPixels(DynamicTexture tex, byte[] pixels) {
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
                } else {
                    // 2..255: Стена с цветом и рельефом
                    // Ванильный метод getColorFromPackedId принимает (colorId << 2) | brightnessId
                    // и возвращает готовый ABGR int с правильным освещением!
                    color = MapColor.getColorFromPackedId(unsigned - 2);
                }

                img.setPixelRGBA(x, y, color);
            }
        }
        tex.upload();
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