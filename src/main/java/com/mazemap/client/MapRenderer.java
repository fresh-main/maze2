package com.mazemap.client;

import com.mazemap.storage.PlayerMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class MapRenderer {
    private static final int PLAYER_DOT = 0xFFB03020;
    private static final int SHADOW = 0x66000000;
    private static final int MARKER_BLUE = 0xFF2670D0;
    private static final int MARKER_OUTLINE = 0xFF142848;
    private static final int PATH_COLOR = 0xFFB03020;
    private static final int COMPASS_RING = 0xFF5A4E3A;
    private static final int COMPASS_N = 0xFFB03020;
    private static final int PAPER_BG = 0xFFE8DCB0;

    private MapRenderer() {}

    /**
     * Универсальный метод отрисовки карты.
     * @param drawCompass рисовать ли компас в левом верхнем углу
     */
    public static void renderMap(GuiGraphics gfx, int x, int y, int w, int h, Player player, boolean drawCompass) {
        if (player == null) return;
        Minecraft mc = Minecraft.getInstance();

        Map<Long, PlayerMapData.Fragment> fragments = ClientMazeMapHandlers.getFragments();
        int fragBlocks = PlayerMapData.FRAGMENT_SIZE_BLOCKS;
        double pxPerBlock = 2.0; // Базовый зум

        double viewX = player.getX();
        double viewZ = player.getZ();
        double worldLeft = viewX - (w / 2.0) / pxPerBlock;
        double worldTop = viewZ - (h / 2.0) / pxPerBlock;

        int cellMinX = (int) Math.floor(worldLeft / fragBlocks);
        int cellMaxX = (int) Math.floor((worldLeft + w / pxPerBlock) / fragBlocks);
        int cellMinZ = (int) Math.floor(worldTop / fragBlocks);
        int cellMaxZ = (int) Math.floor((worldTop + h / pxPerBlock) / fragBlocks);

        // 1. Фрагменты
        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                PlayerMapData.Fragment frag = fragments.get(key);
                if (frag == null) continue;
                byte[] pixels = frag.pixels;
                drawFragment(gfx, pixels, cx, cz, x, y, w, h, worldLeft, worldTop, pxPerBlock);
            }
        }

        // 2. Путь
        List<int[]> path = ClientHudState.getPath();
        if (!path.isEmpty()) {
            int step = Math.max(1, (int) Math.round(2.5 / pxPerBlock));
            for (int i = 0; i < path.size(); i += step) {
                int[] c = path.get(i);
                int sx = x + (int) Math.round((c[0] + 0.5 - worldLeft) * pxPerBlock);
                int sy = y + (int) Math.round((c[1] + 0.5 - worldTop) * pxPerBlock);
                if (sx < x || sx >= x + w || sy < y || sy >= y + h) continue;
                gfx.fill(sx - 1, sy - 1, sx + 1, sy + 1, PATH_COLOR);
            }
        }

        // 3. Метка
        if (ClientHudState.hasMarker()) {
            int mx = ClientHudState.getMarkerX();
            int mz = ClientHudState.getMarkerZ();
            int sx = x + (int) Math.round((mx + 0.5 - worldLeft) * pxPerBlock);
            int sy = y + (int) Math.round((mz + 0.5 - worldTop) * pxPerBlock);
            if (sx >= x - 4 && sx < x + w + 4 && sy >= y - 4 && sy < y + h + 4) {
                gfx.fill(sx - 3, sy, sx + 4, sy + 1, MARKER_OUTLINE);
                gfx.fill(sx, sy - 3, sx + 1, sy + 4, MARKER_OUTLINE);
                gfx.fill(sx - 2, sy, sx + 3, sy + 1, MARKER_BLUE);
                gfx.fill(sx, sy - 2, sx + 1, sy + 3, MARKER_BLUE);
            }
        }

        // 4. Компас
        if (drawCompass) {
            renderCompass(gfx, x + 5, y + 5);
        }

        // 5. ВРАЩАЮЩАЯСЯ СТРЕЛКА ИГРОКА
        int cxs = x + w / 2;
        int cys = y + h / 2;

        gfx.pose().pushPose();
        gfx.pose().translate(cxs, cys, 0);

        // Поворот стрелки.
        // yRot: 0=Юг, -90=Восток, 90=Запад, 180/-180=Север.
        // Мы хотим, чтобы стрелка указывала "вверх" (на Север), когда игрок смотрит на Север.
        // Формула: angle = yRot + 180
        float angle = player.getYRot() + 180f;

        // Для 1.20.1 используем com.mojang.math.Axis.
        // Если у вас 1.20.4+, замените на: gfx.pose().mulPose(new org.joml.Quaternionf().rotationZ(angle * net.minecraft.util.Mth.DEG_TO_RAD));
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));

        // Рисуем стрелку относительно центра (0,0), направленную "вверх" (в -Y)
        // Тень
        gfx.fill(-2, -3, 3, 5, SHADOW);
        // Красное тело
        gfx.fill(-1, -4, 2, 4, PLAYER_DOT);
        gfx.fill(-3, -1, 4, 2, PLAYER_DOT);
        // Блик
        gfx.fill(0, -3, 1, 3, 0xFFFF8080);

        gfx.pose().popPose();
    }

    private static void drawFragment(GuiGraphics gfx, byte[] pixels, int cellX, int cellZ,
                                     int x, int y, int w, int h,
                                     double worldLeft, double worldTop, double pxPerBlock) {
        int fragSize = PlayerMapData.FRAGMENT_SIZE;
        int fragBlocks = PlayerMapData.FRAGMENT_SIZE_BLOCKS;
        int scale = fragSize / fragBlocks;

        long key = ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        double pixelOnScreen = pxPerBlock / scale;
        double fragOriginX = cellX * fragBlocks;
        double fragOriginZ = cellZ * fragBlocks;
        double baseScreenX = (fragOriginX - worldLeft) * pxPerBlock + x;
        double baseScreenY = (fragOriginZ - worldTop) * pxPerBlock + y;

        ResourceLocation tex = FragmentTextureCache.getOrCreate(key, pixels);
        gfx.pose().pushPose();
        gfx.pose().translate((float) baseScreenX, (float) baseScreenY, 0f);
        gfx.pose().scale((float) pixelOnScreen, (float) pixelOnScreen, 1f);
        gfx.blit(tex, 0, 0, 0, 0, fragSize, fragSize, fragSize, fragSize);
        gfx.pose().popPose();
    }

    private static void renderCompass(GuiGraphics gfx, int cx, int cy) {
        int size = 9;
        gfx.fill(cx + 2, cy, cx + size - 2, cy + 1, COMPASS_RING);
        gfx.fill(cx + 2, cy + size - 1, cx + size - 2, cy + size, COMPASS_RING);
        gfx.fill(cx, cy + 2, cx + 1, cy + size - 2, COMPASS_RING);
        gfx.fill(cx + size - 1, cy + 2, cx + size, cy + size - 2, COMPASS_RING);
        gfx.fill(cx + 1, cy + 1, cx + 2, cy + 2, COMPASS_RING);
        gfx.fill(cx + size - 2, cy + 1, cx + size - 1, cy + 2, COMPASS_RING);
        gfx.fill(cx + 1, cy + size - 2, cx + 2, cy + size - 1, COMPASS_RING);
        gfx.fill(cx + size - 2, cy + size - 2, cx + size - 1, cy + size - 1, COMPASS_RING);
        gfx.fill(cx + 1, cy + 2, cx + size - 1, cy + size - 2, PAPER_BG);
        gfx.fill(cx + 2, cy + 1, cx + size - 2, cy + size - 1, PAPER_BG);
        gfx.fill(cx + 4, cy + 2, cx + 5, cy + 5, COMPASS_N);
        gfx.fill(cx + 3, cy + 3, cx + 6, cy + 4, COMPASS_N);
    }
}