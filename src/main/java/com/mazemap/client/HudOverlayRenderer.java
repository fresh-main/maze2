package com.mazemap.client;

import com.mazemap.client.input.MazeMapKeyBindings;
import com.mazemap.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class HudOverlayRenderer {
    private static final int PAPER_BG = 0xFFE8DCB0;
    private static final int PAPER_TINT = 0xFFD9C892;
    private static final int PAPER_EDGE = 0xFF8B7B5A;
    private static final int PAPER_FOLD = 0xFFC4B084;
    private static final int PENCIL_INK = 0xFF2B2418;
    private static final int PENCIL_INK_LIGHT = 0xFF5A4E3A;
    private static final int PENCIL_BODY = 0xFFFFD23F;
    private static final int RULER_BG = 0xFFEEDFA0;
    private static final int RULER_TICK = 0xFF6F5F3F;
    private static final int SHADOW = 0x66000000;
    private static final int FRAME_OUTER = 0xFF2B2418;
    private static final int FRAME_INNER = 0xFF5A4E3A;

    private static final int NOTE_W = 32;
    private static final int NOTE_H = 24;
    private static final int NOTE_MARGIN = 12;
    private static final int MINIMAP_PAPER_W = 100;
    private static final int MINIMAP_PAPER_H = 100;
    private static final int MINIMAP_RULER_W = 8;
    private static final int MINIMAP_PENCIL_W = 6;
    private static final int MINIMAP_TOTAL_W = MINIMAP_PAPER_W + MINIMAP_RULER_W + MINIMAP_PENCIL_W + 4;
    private static final int MINIMAP_MARGIN = 12;
    private static final long ANIM_DURATION_MS = 220L;

    private long noteAnimStart = 0L;
    private boolean noteWasOn = false;
    private long miniAnimStart = 0L;
    private boolean miniWasOn = false;

    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack stack = event.getItemStack();
        if (!stack.is(ModItems.PERSONAL_MAP.get())) return;

        // 【1】 НЕ отменяем событие! Рука и модель предмета отрисуются штатно.
        // event.setCanceled(true);

        // 【2】 Создаем GuiGraphics, используя буфер события руки
        GuiGraphics gfx = new GuiGraphics(mc, (MultiBufferSource.BufferSource) event.getMultiBufferSource());
        var pose = event.getPoseStack();

        pose.pushPose();

        // 【3】 Трансформируем позицию и масштаб, чтобы UI-карта легла на ладонь
        // 0.0055f подобрано так, чтобы 100px карта стала размером ~0.55 блока (как предмет в руке)
        float scale = 0.0055F;
        pose.scale(scale, scale, scale);

        // Сдвиг к центру ладони (Y вверх, Z вперед от игрока)
        // Значения могут потребовать微调 под вашу модель руки, но работают стабильно
        pose.translate(0.0D, 12.0D, 22.0D);

        // Рендерим карту в координатах 0,0 (сдвиг уже применен к PoseStack)
        renderMiniMap(gfx, 0, 0, mc.player);

        pose.popPose();
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen != null) return;

        // 【3】 Если карта уже в руке, пропускаем рендер в HUD, чтобы не было дублей
        if (isMapHeld(mc)) return;

        GuiGraphics gfx = event.getGuiGraphics();
        int sw = event.getWindow().getGuiScaledWidth();
        int sh = event.getWindow().getGuiScaledHeight();

        boolean miniOnNow = MazeMapKeyBindings.HOLD_MINIMAP.isDown();
        boolean noteOnNow = ClientHudState.isNoteHeld();
        long now = System.currentTimeMillis();

        if (miniOnNow != miniWasOn) { miniWasOn = miniOnNow; miniAnimStart = now; }
        if (noteOnNow != noteWasOn) { noteWasOn = noteOnNow; noteAnimStart = now; }

        float miniProgress = computeProgress(miniAnimStart, miniOnNow, now);
        float noteProgress = computeProgress(noteAnimStart, noteOnNow, now);

        int miniBaseX = sw - MINIMAP_TOTAL_W - MINIMAP_MARGIN;
        int miniBaseY = sh - MINIMAP_PAPER_H - MINIMAP_MARGIN - 24;
        if (miniProgress > 0.001f) {
            int offset = (int) Math.round((1f - miniProgress) * (MINIMAP_TOTAL_W + MINIMAP_MARGIN + 8));
            renderMiniMap(gfx, miniBaseX + offset, miniBaseY, mc.player);
        }

        int outerNoteX = sw - NOTE_W - NOTE_MARGIN;
        int innerNoteX = miniBaseX - NOTE_W - 8;
        int noteBaseX = (int) Math.round(outerNoteX + (innerNoteX - outerNoteX) * miniProgress);
        int noteBaseY = sh - NOTE_H - NOTE_MARGIN - 24;
        if (noteProgress > 0.001f) {
            int offset = (int) Math.round((1f - noteProgress) * (NOTE_H + NOTE_MARGIN + 24));
            renderNoteIcon(gfx, noteBaseX, noteBaseY + offset, sw);
        }
    }

    /** Проверка, держит ли игрок карту в любой руке */
    private boolean isMapHeld(Minecraft mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().is(ModItems.PERSONAL_MAP.get()) ||
                mc.player.getOffhandItem().is(ModItems.PERSONAL_MAP.get());
    }

    private void renderMiniMap(GuiGraphics gfx, int totalX, int totalY, net.minecraft.world.entity.player.Player player) {
        int rulerX = totalX;
        int paperX = totalX + MINIMAP_RULER_W + 2;
        int pencilX = paperX + MINIMAP_PAPER_W + 2;
        int paperY = totalY;
        int paperW = MINIMAP_PAPER_W;
        int paperH = MINIMAP_PAPER_H;

        gfx.fill(paperX + 2, paperY + 3, paperX + paperW + 3, paperY + paperH + 3, SHADOW);
        renderMiniRuler(gfx, rulerX, paperY, paperH);
        gfx.fill(paperX, paperY, paperX + paperW, paperY + paperH, PAPER_BG);

        for (int i = 0; i < paperH; i += 7) {
            gfx.fill(paperX + 8, paperY + i, paperX + paperW - 8, paperY + i + 1, PAPER_TINT);
        }

        gfx.fill(paperX, paperY, paperX + paperW, paperY + 1, PAPER_EDGE);
        gfx.fill(paperX, paperY + paperH - 1, paperX + paperW, paperY + paperH, PAPER_EDGE);
        gfx.fill(paperX, paperY, paperX + 1, paperY + paperH, PAPER_EDGE);
        gfx.fill(paperX + paperW - 1, paperY, paperX + paperW, paperY + paperH, PAPER_EDGE);

        int foldSize = 8;
        for (int i = 0; i < foldSize; i++) {
            int triW = foldSize - i;
            gfx.fill(paperX + paperW - triW, paperY + i, paperX + paperW, paperY + i + 1, PAPER_FOLD);
        }
        for (int i = 0; i < foldSize; i++) {
            gfx.fill(paperX + paperW - foldSize + i, paperY + foldSize - 1 - i, paperX + paperW - foldSize + i + 1, paperY + foldSize - i, PAPER_EDGE);
        }

        int mapX = paperX + 2;
        int mapY = paperY + 2;
        int mapW = paperW - 4;
        int mapH = paperH - 4;

        // 【ВАЖНО】 Убран enableScissor, так как в 3D-руке он работает некорректно.
        // Рамка бумаги и так ограничивает область видимости.
        MapRenderer.renderMap(gfx, mapX, mapY, mapW, mapH, player, false);

        renderMiniPencil(gfx, pencilX, paperY, paperH);
    }

    private void renderNoteIcon(GuiGraphics gfx, int x, int y, int screenW) {
        gfx.fill(x + 2, y + 2, x + NOTE_W + 2, y + NOTE_H + 2, SHADOW);
        gfx.fill(x, y, x + NOTE_W, y + NOTE_H, PAPER_BG);
        gfx.fill(x, y, x + NOTE_W, y + 1, PAPER_EDGE);
        gfx.fill(x, y + NOTE_H - 1, x + NOTE_W, y + NOTE_H, PAPER_EDGE);
        gfx.fill(x, y, x + 1, y + NOTE_H, PAPER_EDGE);
        gfx.fill(x + NOTE_W - 1, y, x + NOTE_W, y + NOTE_H, PAPER_EDGE);
        for (int i = 0; i < 5; i++) {
            gfx.fill(x + NOTE_W - 5 + i, y + 1, x + NOTE_W - 4 + i, y + 5 - i + 1, PAPER_EDGE);
        }
        gfx.fill(x + NOTE_W - 5, y + 1, x + NOTE_W - 1, y + 5, PAPER_TINT);
        for (int line = 0; line < 4; line++) {
            int ly = y + 5 + line * 4;
            int xStart = x + 4;
            int xEnd = x + NOTE_W - 7 - (line % 2) * 4;
            gfx.fill(xStart, ly, xEnd, ly + 1, line == 0 ? PENCIL_INK : PENCIL_INK_LIGHT);
        }
        Font font = Minecraft.getInstance().font;
        String keyName = MazeMapKeyBindings.TOGGLE_NOTE.getKey().getDisplayName().getString();
        Component hint = Component.literal("§7[ " + keyName + " ] передать ");
        int tw = font.width(hint);
        int hintX = Math.max(2, Math.min(x + NOTE_W / 2 - tw / 2, screenW - tw - 2));
        gfx.drawString(font, hint, hintX, y - 10, 0xFFFFFF, true);
    }

    private void renderMiniRuler(GuiGraphics gfx, int x, int y, int h) {
        gfx.fill(x, y, x + MINIMAP_RULER_W, y + h, RULER_BG);
        gfx.fill(x, y, x + 1, y + h, PAPER_EDGE);
        gfx.fill(x + MINIMAP_RULER_W - 1, y, x + MINIMAP_RULER_W, y + h, PAPER_EDGE);
        for (int i = 2; i < h - 2; i += 4) {
            int len = (i % 16 == 0) ? 5 : 3;
            gfx.fill(x + MINIMAP_RULER_W - 1 - len, y + i, x + MINIMAP_RULER_W - 1, y + i + 1, RULER_TICK);
        }
    }

    private void renderMiniPencil(GuiGraphics gfx, int x, int y, int h) {
        gfx.fill(x, y + 6, x + MINIMAP_PENCIL_W, y + h - 12, PENCIL_BODY);
        gfx.fill(x - 1, y + 3, x + MINIMAP_PENCIL_W + 1, y + 6, 0xFF888888);
        gfx.fill(x, y, x + MINIMAP_PENCIL_W, y + 3, 0xFFE391B0);
        for (int i = 0; i < 6; i++) {
            int w = MINIMAP_PENCIL_W - i;
            if (w <= 0) break;
            int xx = x + (MINIMAP_PENCIL_W - w) / 2;
            gfx.fill(xx, y + h - 12 + i, xx + w, y + h - 12 + i + 1, i < 4 ? 0xFFD9A86B : PENCIL_INK);
        }
    }

    private static float computeProgress(long startMs, boolean targetOn, long now) {
        long elapsed = Math.max(0, now - startMs);
        float t = Math.min(1f, elapsed / (float) ANIM_DURATION_MS);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        return targetOn ? eased : 1f - eased;
    }
}