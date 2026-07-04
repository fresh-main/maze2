package com.mazemap.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.HashMap;
import java.util.Map;

public class PlayerMapData {
    public static final int FRAGMENT_SIZE = 64;
    public static final int FRAGMENT_SIZE_BLOCKS = 32;

    // ИСПРАВЛЕНИЕ: Добавляем константы размеров массивов
    public static final int PIXEL_COUNT = FRAGMENT_SIZE * FRAGMENT_SIZE;
    public static final int WALKABLE_BYTES = PIXEL_COUNT; // 1 байт на пиксель проходимости

    public static final byte PIXEL_UNEXPLORED = 0;
    public static final byte PIXEL_PASSAGE = 1;

    // ИСПРАВЛЕНИЕ: Внутренний класс для хранения и цветов, и проходимости
    public static class Fragment {
        public final byte[] pixels;
        public final byte[] walkable;

        public Fragment() {
            this.pixels = new byte[PIXEL_COUNT];
            this.walkable = new byte[WALKABLE_BYTES];
        }

        public Fragment(byte[] pixels, byte[] walkable) {
            this.pixels = pixels;
            this.walkable = walkable;
        }
    }

    private final Map<Long, Fragment> fragments = new HashMap<>();
    private boolean dirty = false;

    public static boolean isWallPixel(byte b) { return (b & 0xFF) >= 2; }
    public static boolean isExploredPassagePixel(byte b) { return (b & 0xFF) == 1; }

    public Fragment getFragment(int cellX, int cellZ) {
        return fragments.get(key(cellX, cellZ));
    }

    public Fragment getOrCreateFragment(int cellX, int cellZ) {
        return fragments.computeIfAbsent(key(cellX, cellZ), k -> {
            dirty = true;
            return new Fragment();
        });
    }

    public void putFragment(int cellX, int cellZ, Fragment frag) {
        fragments.put(key(cellX, cellZ), frag);
        dirty = true;
    }

    public Map<Long, Fragment> getAllFragments() {
        return fragments;
    }

    public boolean isDirty() { return dirty; }
    public void markDirty() { this.dirty = true; }
    public void clearDirty() { this.dirty = false; }

    private static long key(int cellX, int cellZ) {
        return (((long) cellX) << 32) | (cellZ & 0xFFFFFFFFL);
    }

    public static int unpackCellX(long key) { return (int) (key >> 32); }
    public static int unpackCellZ(long key) { return (int) key; }

    public CompoundTag toNbt() {
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        ListTag list = new ListTag();
        for (Map.Entry<Long, Fragment> e : fragments.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", unpackCellX(e.getKey()));
            tag.putInt("z", unpackCellZ(e.getKey()));
            tag.putByteArray("p", e.getValue().pixels);
            tag.putByteArray("w", e.getValue().walkable); // Сохраняем проходимость
            list.add(tag);
        }
        root.put("fragments", list);
        return root;
    }

    public static PlayerMapData fromNbt(CompoundTag root) {
        PlayerMapData data = new PlayerMapData();
        ListTag list = root.getList("fragments", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int x = t.getInt("x");
            int z = t.getInt("z");
            byte[] pixels = t.getByteArray("p");
            byte[] walkable = t.getByteArray("w");
            if (pixels.length == PIXEL_COUNT) {
                if (walkable.length != WALKABLE_BYTES) walkable = new byte[WALKABLE_BYTES];
                data.fragments.put(key(x, z), new Fragment(pixels, walkable));
            }
        }
        return data;
    }
}