package com.labyrinthmod.client.video_source;

import com.labyrinthmod.common.network.packet.MazeMonitorMapPacket;
import com.labyrinthmod.common.network.packet.MazeMonitorSyncPacket;
import com.labyrinthmod.common.network.packet.OpenAdminMenuPacket;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MazeMonitorClientData {

    private MazeMonitorClientData() {}

    // Текстура кэшируется здесь, а не в IVideoSource: Vista создаёт новый
    // IVideoSource на КАЖДЫЙ кадр (BroadcastVideoSource.getVideoFrameBuilder
    // вызывает IBroadcastProvider.getBroadcastVideoSource() без кэширования).
    public static class MonitorData {
        public BlockPos boundsMin, boundsMax;
        public int mapWidth, mapHeight;
        public byte[] mapData = new byte[0];
        public List<OpenAdminMenuPacket.PlayerSnapshot> players = new ArrayList<>();
        public List<MazeMonitorSyncPacket.GriverDot> grivers = new ArrayList<>();
        public boolean dirty = true;
        public DynamicTexture texture;
        public ResourceLocation textureLoc;
    }

    private static final Map<UUID, MonitorData> DATA = new HashMap<>();

    public static MonitorData get(UUID id) {
        return DATA.computeIfAbsent(id, k -> new MonitorData());
    }

    public static void applyMap(MazeMonitorMapPacket p) {
        MonitorData d = get(p.monitorId);
        d.boundsMin = p.boundsMin;
        d.boundsMax = p.boundsMax;
        d.mapWidth = p.mapWidth;
        d.mapHeight = p.mapHeight;
        d.mapData = p.mapData;
        d.dirty = true;
    }

    public static void applySync(MazeMonitorSyncPacket p) {
        MonitorData d = get(p.monitorId);
        d.players = p.players;
        d.grivers = p.grivers;
        d.dirty = true;
    }
}
