package com.mazemap.client.screen;
import com.mazemap.client.ClientHudState;
import com.mazemap.client.ClientMazeMapHandlers;
import com.mazemap.client.FragmentTextureCache;
import com.mazemap.client.MazePathFinder;
import com.mazemap.item.PersonalMapItem;
import com.mazemap.storage.PlayerMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class PersonalMapScreen extends Screen {
    private static final int PAPER_BG = 0xFFE8DCB0;
    private static final int PAPER_TINT = 0xFFD9C892;
    private static final int PAPER_EDGE = 0xFF8B7B5A;
    private static final int PENCIL_INK = 0xFF2B2418;
    private static final int PENCIL_BODY = 0xFFFFD23F;
    private static final int PENCIL_TIP = 0xFF1A1A1A;
    private static final int RULER_BG = 0xFFEEDFA0;
    private static final int RULER_TICK = 0xFF6F5F3F;
    private static final int PLAYER_DOT = 0xFFB03020;
    private static final int SHADOW = 0x66000000;
    private static final int MARKER_RED = 0xFF2670D0;
    private static final int MARKER_OUTLINE = 0xFF142848;
    private static final int PATH_COLOR = 0xFFB03020;
    private static final long OPEN_DURATION_MS = 420L;

    private double viewX = 0;
    private double viewZ = 0;
    private double zoom = 2.0;
    private boolean dragging = false;
    private double lastDragX, lastDragZ;
    private long openTimeMs = 0L;

    public PersonalMapScreen() {
        super(Component.literal("Личная карта"));
    }

    @Override
    protected void init() {
        super.init();
        if (Minecraft.getInstance().player != null) {
            viewX = Minecraft.getInstance().player.getX();
            viewZ = Minecraft.getInstance().player.getZ();
        }
        openTimeMs = System.currentTimeMillis();
        ItemStack stack = PersonalMapItem.findInInventory(Minecraft.getInstance().player);
        if (!stack.isEmpty() && PersonalMapItem.hasMarker(stack)) {
            ClientHudState.setMarker(PersonalMapItem.getMarkerX(stack), PersonalMapItem.getMarkerZ(stack));
            recomputePath();
        }
        recomputePath();
    }

    private int paperX() { return this.width / 2 - paperW() / 2; }
    private int paperY() { return this.height / 2 - paperH() / 2; }
    private int paperW() { return Math.min(this.width - 80, 400); }
    private int paperH() { return Math.min(this.height - 80, 320); }

    private float openProgress() {
        long elapsed = System.currentTimeMillis() - openTimeMs;
        if (elapsed < 0) elapsed = 0;
        float t = Math.min(1f, elapsed / (float) OPEN_DURATION_MS);
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        gfx.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        float p = openProgress();
        if (p < 0.01f) {
            super.render(gfx, mouseX, mouseY, partialTicks);
            return;
        }

        int px = paperX(), py = paperY(), pw = paperW(), ph = paperH();
        int cx = px + pw / 2;
        int cy = py + ph / 2;

        gfx.pose().pushPose();
        gfx.pose().translate(cx, cy, 0);
        gfx.pose().scale(1f, p, 1f);
        gfx.pose().translate(-cx, -cy, 0);

        gfx.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, SHADOW);
        gfx.fill(px, py, px + pw, py + ph, PAPER_BG);
        for (int i = 0; i < ph; i += 8) {
            gfx.fill(px + 10, py + i, px + pw - 10, py + i + 1, PAPER_TINT);
        }
        gfx.fill(px, py, px + pw, py + 2, PAPER_EDGE);
        gfx.fill(px, py + ph - 2, px + pw, py + ph, PAPER_EDGE);
        gfx.fill(px, py, px + 2, py + ph, PAPER_EDGE);
        gfx.fill(px + pw - 2, py, px + pw, py + ph, PAPER_EDGE);

        renderRuler(gfx, px - 16, py, ph);
        renderPencil(gfx, px + pw + 6, py, ph);

        int mapX = px + 6, mapY = py + 6;
        int mapW = pw - 12, mapH = ph - 12;

        if (p > 0.95f) { gfx.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH); }
        renderMap(gfx, mapX, mapY, mapW, mapH);
        if (p > 0.95f) { gfx.disableScissor(); }

        gfx.pose().popPose();
        gfx.drawCenteredString(this.font, "§7ЛКМ+Тянуть - двигать | Колесо - зум | ПКМ - метка | ESC - закрыть",
                this.width / 2, py + ph + 12, 0xFFFFFF);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private void renderRuler(GuiGraphics gfx, int x, int y, int h) {
        gfx.fill(x, y, x + 12, y + h, RULER_BG);
        gfx.fill(x, y, x + 1, y + h, PAPER_EDGE);
        gfx.fill(x + 11, y, x + 12, y + h, PAPER_EDGE);
        for (int i = 0; i < h; i += 8) {
            int len = (i % 32 == 0) ? 8 : 4;
            gfx.fill(x + 12 - len, y + i, x + 12, y + i + 1, RULER_TICK);
        }
    }

    private void renderPencil(GuiGraphics gfx, int x, int y, int h) {
        gfx.fill(x, y + 8, x + 8, y + h - 16, PENCIL_BODY);
        gfx.fill(x - 1, y + 4, x + 9, y + 8, 0xFF888888);
        gfx.fill(x, y, x + 8, y + 4, 0xFFE391B0);
        for (int i = 0; i < 8; i++) {
            int w = 8 - i;
            int xx = x + (8 - w) / 2;
            gfx.fill(xx, y + h - 16 + i, xx + w, y + h - 16 + i + 1,
                    i < 6 ? 0xFFD9A86B : PENCIL_TIP);
        }
    }

    private void renderMap(GuiGraphics gfx, int x, int y, int w, int h) {
        Map<Long, PlayerMapData.Fragment> fragments = ClientMazeMapHandlers.getFragments();
        int fragBlocks = PlayerMapData.FRAGMENT_SIZE_BLOCKS;
        double pxPerBlock = 2.0 * zoom;
        double worldLeft = viewX - (w / 2.0) / pxPerBlock;
        double worldTop = viewZ - (h / 2.0) / pxPerBlock;

        int cellMinX = (int) Math.floor(worldLeft / fragBlocks);
        int cellMaxX = (int) Math.floor((worldLeft + w / pxPerBlock) / fragBlocks);
        int cellMinZ = (int) Math.floor(worldTop / fragBlocks);
        int cellMaxZ = (int) Math.floor((worldTop + h / pxPerBlock) / fragBlocks);

        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                PlayerMapData.Fragment frag = fragments.get(key);
                if (frag == null) continue;
                drawFragment(gfx, frag.pixels, cx, cz, x, y, w, h, worldLeft, worldTop, pxPerBlock);
            }
        }

        List<int[]> path = ClientHudState.getPath();
        if (!path.isEmpty()) renderPath(gfx, path, x, y, w, h, worldLeft, worldTop, pxPerBlock);

        if (ClientHudState.hasMarker()) {
            renderMarker(gfx, ClientHudState.getMarkerX(), ClientHudState.getMarkerZ(),
                    x, y, w, h, worldLeft, worldTop, pxPerBlock);
        }

        if (Minecraft.getInstance().player != null) {
            double pxBlock = Minecraft.getInstance().player.getX() - worldLeft;
            double pzBlock = Minecraft.getInstance().player.getZ() - worldTop;
            int sx = x + (int) Math.round(pxBlock * pxPerBlock);
            int sy = y + (int) Math.round(pzBlock * pxPerBlock);

            if (sx >= x - 10 && sx < x + w + 10 && sy >= y - 10 && sy < y + h + 10) {
                gfx.pose().pushPose();
                gfx.pose().translate(sx, sy, 0);
                float angle = Minecraft.getInstance().player.getYRot() + 180f;
                gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
                gfx.fill(-2, -3, 3, 5, SHADOW);
                gfx.fill(-1, -4, 2, 4, PLAYER_DOT);
                gfx.fill(-3, -1, 4, 2, PLAYER_DOT);
                gfx.fill(0, -3, 1, 3, 0xFFFF8080);
                gfx.pose().popPose();
            }
        }
    }

    private void drawFragment(GuiGraphics gfx, byte[] pixels, int cellX, int cellZ,
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

    private void renderPath(GuiGraphics gfx, List<int[]> path,
                            int x, int y, int w, int h,
                            double worldLeft, double worldTop, double pxPerBlock) {
        int step = Math.max(1, (int) Math.round(2.5 / pxPerBlock));
        for (int i = 0; i < path.size(); i += step) {
            int[] c = path.get(i);
            int sx = x + (int) Math.round((c[0] + 0.5 - worldLeft) * pxPerBlock);
            int sy = y + (int) Math.round((c[1] + 0.5 - worldTop) * pxPerBlock);
            if (sx < x || sx >= x + w || sy < y || sy >= y + h) continue;
            gfx.fill(sx - 1, sy - 1, sx + 2, sy + 2, PATH_COLOR);
        }
    }

    private void renderMarker(GuiGraphics gfx, int markerX, int markerZ,
                              int x, int y, int w, int h,
                              double worldLeft, double worldTop, double pxPerBlock) {
        int sx = x + (int) Math.round((markerX + 0.5 - worldLeft) * pxPerBlock);
        int sy = y + (int) Math.round((markerZ + 0.5 - worldTop) * pxPerBlock);
        if (sx < x - 8 || sx >= x + w + 8 || sy < y - 8 || sy >= y + h + 8) return;
        gfx.fill(sx - 4, sy - 1, sx + 5, sy + 2, MARKER_OUTLINE);
        gfx.fill(sx - 1, sy - 4, sx + 2, sy + 5, MARKER_OUTLINE);
        gfx.fill(sx - 3, sy, sx + 4, sy + 1, MARKER_RED);
        gfx.fill(sx, sy - 3, sx + 1, sy + 4, MARKER_RED);
    }

    private void recomputePath() {
        if (!ClientHudState.hasMarker() || Minecraft.getInstance().player == null) return;
        int sx = (int) Math.floor(Minecraft.getInstance().player.getX());
        int sz = (int) Math.floor(Minecraft.getInstance().player.getZ());
        int mx = ClientHudState.getMarkerX();
        int mz = ClientHudState.getMarkerZ();
        ClientHudState.setPath(MazePathFinder.findPath(sx, sz, mx, mz));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;
        int x = paperX() + 6, y = paperY() + 6;
        int w = paperW() - 12, h = paperH() - 12;
        boolean inMap = mx >= x && mx < x + w && my >= y && my < y + h;
        if (button == 1 && inMap) {
            double pxPerBlock = 2.0 * zoom;
            double worldLeft = viewX - (w / 2.0) / pxPerBlock;
            double worldTop = viewZ - (h / 2.0) / pxPerBlock;
            int worldX = (int) Math.floor((mx - x) / pxPerBlock + worldLeft);
            int worldZ = (int) Math.floor((my - y) / pxPerBlock + worldTop);

            ItemStack mapStack = PersonalMapItem.findInInventory(Minecraft.getInstance().player);

            if (ClientHudState.hasMarker()
                    && ClientHudState.getMarkerX() == worldX
                    && ClientHudState.getMarkerZ() == worldZ) {
                ClientHudState.clearMarker();
                if (!mapStack.isEmpty()) PersonalMapItem.clearMarker(mapStack); // 💾 Сохраняем удаление
            } else {
                ClientHudState.setMarker(worldX, worldZ);
                recomputePath();
                if (!mapStack.isEmpty()) PersonalMapItem.setMarker(mapStack, worldX, worldZ); // 💾 Сохраняем установку
            }
            return true;
        }

        if (button == 0 && inMap) {
            dragging = true;
            lastDragX = mouseX;
            lastDragZ = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            double pxPerBlock = 2.0 * zoom;
            viewX -= (mouseX - lastDragX) / pxPerBlock;
            viewZ -= (mouseY - lastDragZ) / pxPerBlock;
            lastDragX = mouseX;
            lastDragZ = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        zoom = Math.max(0.5, Math.min(8.0, zoom * (delta > 0 ? 1.2 : 1 / 1.2)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ===== 【ГЛАВНОЕ ИСПРАВЛЕНИЕ】 =====
    @Override
    public void onClose() {
        // Мы НЕ вызываем ClientHudState.clearMarker() и НЕ очищаем путь.
        // Это позволяет метке и рассчитанному маршруту остаться в памяти
        // и продолжить отображаться в HUD/на мини-карте после закрытия экрана.
        super.onClose();
    }
}