package com.labyrinthmod.client.video_source;

import com.labyrinthmod.common.block.entity.MazeMonitorBlockEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.mehvahdjukaar.vista.client.video_source.IVideoSource;
import net.mehvahdjukaar.vista.common.tv.IntAnimationState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.UUID;

/**
 * Рисует карту лабиринта обычным NativeImage/DynamicTexture (как AdminScreen),
 * а не через GL-фреймбуфер Vista/Moonlight — у их drawAsInGUI фиксированное
 * ортографическое окно 16x16 единиц, не подходящее для детальной карты.
 *
 * Vista создаёт новый IVideoSource каждый кадр (BroadcastVideoSource не
 * кэширует результат IBroadcastProvider.getBroadcastVideoSource()), поэтому
 * сам этот класс не хранит состояния — вся текстура кэшируется в
 * MazeMonitorClientData.MonitorData по monitorId.
 */
public class MazeMonitorVideoSource implements IVideoSource {

    private static final int TEX_WIDTH = 256;
    private static final int TEX_HEIGHT = 256;

    private final UUID monitorId;

    public MazeMonitorVideoSource(MazeMonitorBlockEntity be) {
        this.monitorId = be.getUUID();
    }

    @Override
    public VertexConsumer getVideoFrameBuilder(float partialTicks, MultiBufferSource bufferSource,
                                               boolean arg2, int arg3, int arg4, int arg5, boolean arg6,
                                               IntAnimationState fade, IntAnimationState enderman) {
        MazeMonitorClientData.MonitorData data = MazeMonitorClientData.get(monitorId);
        ensureTexture(data);

        if (data.dirty) {
            redraw(data);
            data.dirty = false;
        }

        return bufferSource.getBuffer(RenderType.entitySolid(data.textureLoc));
    }

    private void ensureTexture(MazeMonitorClientData.MonitorData data) {
        if (data.texture != null) return;
        NativeImage img = new NativeImage(TEX_WIDTH, TEX_HEIGHT, false);
        data.texture = new DynamicTexture(img);
        data.textureLoc = Minecraft.getInstance().getTextureManager()
                .register("labyrinthmod_monitor_" + monitorId, data.texture);
    }

    private void redraw(MazeMonitorClientData.MonitorData data) {
        NativeImage img = data.texture.getPixels();
        if (img == null) return;

        for (int x = 0; x < TEX_WIDTH; x++) {
            for (int y = 0; y < TEX_HEIGHT; y++) {
                img.setPixelRGBA(x, y, 0xFF1A1A1A);
            }
        }

        if (data.boundsMin != null && data.boundsMax != null) {
            int minX = Math.min(data.boundsMin.getX(), data.boundsMax.getX());
            int minZ = Math.min(data.boundsMin.getZ(), data.boundsMax.getZ());
            int spanX = Math.abs(data.boundsMax.getX() - data.boundsMin.getX()) + 1;
            int spanZ = Math.abs(data.boundsMax.getZ() - data.boundsMin.getZ()) + 1;
            double scale = Math.min((double) TEX_WIDTH / spanX, (double) TEX_HEIGHT / spanZ);
            int offX = (int) ((TEX_WIDTH - spanX * scale) / 2);
            int offZ = (int) ((TEX_HEIGHT - spanZ * scale) / 2);

            if (data.mapData != null && data.mapData.length > 0
                    && data.mapWidth > 0 && data.mapHeight > 0) {
                int cellSize = Math.max(1, (int) Math.ceil(scale));
                for (int i = 0; i < data.mapWidth; i++) {
                    for (int j = 0; j < data.mapHeight; j++) {
                        boolean walkable = data.mapData[i * data.mapHeight + j] != 0;
                        int color = walkable ? 0xFF3A6E3A : 0xFFBBBBBB;
                        int px = offX + (int) (i * scale);
                        int pz = offZ + (int) (j * scale);
                        fillRect(img, px, pz, cellSize, cellSize, color);
                    }
                }
            }

            for (var g : data.grivers) {
                int px = offX + (int) ((g.pos.getX() - minX) * scale);
                int pz = offZ + (int) ((g.pos.getZ() - minZ) * scale);
                fillRect(img, px - 2, pz - 2, 5, 5, 0xFFFF5555);
            }

            for (var pl : data.players) {
                int px = offX + (int) ((pl.pos.getX() - minX) * scale);
                int pz = offZ + (int) ((pl.pos.getZ() - minZ) * scale);
                fillRect(img, px - 1, pz - 1, 3, 3, 0xFF55AAFF);
            }
        }

        data.texture.upload();
    }

    private void fillRect(NativeImage img, int x, int y, int w, int h, int color) {
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int px = x + dx, py = y + dy;
                if (px >= 0 && px < TEX_WIDTH && py >= 0 && py < TEX_HEIGHT) {
                    img.setPixelRGBA(px, py, color);
                }
            }
        }
    }
}