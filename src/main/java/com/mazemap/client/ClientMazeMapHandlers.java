package com.mazemap.client;

import com.labyrinthmod.LabyrinthMod;
import com.mazemap.client.render.MapHandRenderer; // <-- НОВЫЙ ИМПОРТ
import com.mazemap.client.screen.PersonalMapScreen;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.network.packet.S2CFragmentSyncPacket;
import com.mazemap.storage.PlayerMapData;
import com.mazemap.util.MapDurabilityHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class ClientMazeMapHandlers {
    private static final Map<Long, PlayerMapData.Fragment> FRAGMENTS = new ConcurrentHashMap<>();
    private ClientMazeMapHandlers() {}

    public static Map<Long, PlayerMapData.Fragment> getFragments() {
        return FRAGMENTS;
    }



    public static void handleOpenMap() {
        LabyrinthMod.LOGGER.info("[MazeMap] S2COpenMapPacket received (cache has {} fragments)", FRAGMENTS.size());
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof PersonalMapScreen) return;
        try {
            Minecraft.getInstance().setScreen(new PersonalMapScreen());
        } catch (Throwable t) {
            LabyrinthMod.LOGGER.error("[MazeMap] Failed to open PersonalMapScreen", t);
        }
    }

    public static void handleClearMap() {
        FRAGMENTS.clear();
        FragmentTextureCache.clear();
        // ClientHudState.clearMarker(); // ❌ УДАЛИТЬ или ЗАКОММЕНТИРОВАТЬ
        MapHandRenderer.forceMapTextureUpload();
    }
    public static void handleFragmentSync(S2CFragmentSyncPacket p) {
        long key = ((long) p.cellX << 32) | (p.cellZ & 0xFFFFFFFFL);
        PlayerMapData.Fragment frag = FRAGMENTS.get(key);
        if (frag == null) {
            frag = new PlayerMapData.Fragment(p.pixels, p.walkable);
            FRAGMENTS.put(key, frag);
        } else {
            System.arraycopy(p.pixels, 0, frag.pixels, 0, p.pixels.length);
            System.arraycopy(p.walkable, 0, frag.walkable, 0, p.walkable.length);
        }
        FragmentTextureCache.invalidate(key);
        MapHandRenderer.forceMapTextureUpload();
    }
}