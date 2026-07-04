package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

public final class OtborHudOverlay {

    private OtborHudOverlay() {}

    private static final int CELL_W = 22;
    private static final int CELL_H = 26;
    private static final int GAP = 3;

    public static void renderHotbar(GuiGraphics gfx, int screenW, int screenH, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || p.isSpectator()) return;

        Font font = mc.font;
        int selected = p.getInventory().selected;

        int totalW = CELL_W * 9 + GAP * 8;
        int startX = screenW / 2 - totalW / 2;
        int baseY = screenH - CELL_H - 6;

        for (int i = 0; i < 9; i++) {
            int sx = startX + i * (CELL_W + GAP);
            boolean active = (i == selected);
            float rot = ((i * 37) % 9) - 4f;
            int liftY = active ? -6 : 0;
            drawHotbarSticker(gfx, font, p, i, sx, baseY + liftY, active, rot);
        }

        ItemStack offhand = p.getOffhandItem();
        boolean rightArm = p.getMainArm() == HumanoidArm.RIGHT;
        int offX = rightArm ? startX - CELL_W - 10 : startX + totalW + 10;
        drawOffhandSticker(gfx, font, p, offhand, offX, baseY);

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float f = p.getAttackStrengthScale(0.0f);
            if (f < 1.0f) {
                int ix = rightArm ? startX + totalW + 4 : startX - 10;
                drawAttackIndicator(gfx, ix, baseY, f);
            }
        }
    }

    private static void drawHotbarSticker(GuiGraphics gfx, Font font, Player p, int i,
                                           int x, int y, boolean active, float rotationDeg) {
        gfx.pose().pushPose();
        gfx.pose().translate(x + CELL_W / 2f, y + CELL_H / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotationDeg));
        gfx.pose().translate(-CELL_W / 2f, -CELL_H / 2f, 0);

        int paperColor = active ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE;
        // drawPaperCard: 6 fill-вызовов вместо ~470 у drawPaper. Хотбар рендерится
        // КАЖДЫЙ кадр, 9 ячеек — на старом drawPaper это давало >4000 fill-вызовов
        // в кадр только за хотбар, что заметно роняло FPS особенно с другими модами.
        PaperRender.drawPaperCard(gfx, 0, 0, CELL_W, CELL_H, 1.0f, paperColor);

        if (active) {
            int rc = PaperRender.INK_RED;
            gfx.fill(0, 0, CELL_W, 1, rc);
            gfx.fill(0, CELL_H - 1, CELL_W, CELL_H, rc);
            gfx.fill(0, 0, 1, CELL_H, rc);
            gfx.fill(CELL_W - 1, 0, CELL_W, CELL_H, rc);
        }

        PaperRender.drawPin(gfx, CELL_W / 2, 2, !active);

        String num = String.valueOf(i + 1);
        gfx.drawString(font, num, 2, 2, PaperRender.INK_FADED, false);

        // renderItem не поддерживает вращённую pose корректно — рендерим вне локального поворота.
        gfx.pose().popPose();

        ItemStack stack = p.getInventory().items.get(i);
        if (!stack.isEmpty()) {
            int ix = x + (CELL_W - 16) / 2;
            int iy = y + (CELL_H - 16) / 2;
            gfx.renderItem(p, stack, ix, iy, i);
            gfx.renderItemDecorations(font, stack, ix, iy);
        }
    }

    private static void drawOffhandSticker(GuiGraphics gfx, Font font, Player p, ItemStack offhand,
                                            int x, int y) {
        int w = CELL_W + 4;
        int h = CELL_H + 4;
        gfx.pose().pushPose();
        gfx.pose().translate(x + w / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-2.5f));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);

        // Per-frame HUD — drawPaperCard, не drawPaper, во избежание ~470 fill-вызовов на кадр.
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, w / 2, 2, true);
        gfx.drawString(font, "оф", 2, 2, PaperRender.INK_FADED, false);
        gfx.pose().popPose();

        if (!offhand.isEmpty()) {
            int ix = x + (w - 16) / 2;
            int iy = y + (h - 16) / 2;
            gfx.renderItem(p, offhand, ix, iy, 0);
            gfx.renderItemDecorations(font, offhand, ix, iy);
        }
    }

    private static void drawAttackIndicator(GuiGraphics gfx, int x, int y, float f) {
        int barH = CELL_H;
        int barW = 4;
        gfx.fill(x, y, x + barW, y + barH, 0xA0000000);
        int filled = (int) (f * (barH - 2));
        gfx.fill(x + 1, y + 1 + (barH - 2 - filled), x + barW - 1, y + barH - 1,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.9f));
    }

    public static void renderFood(GuiGraphics gfx, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !p.isAlive() || p.isSpectator()) return;

        Font font = mc.font;
        int cardW = 140;
        int cardH = 64;
        int x = 12;
        int y = screenH - cardH - 12;

        gfx.pose().pushPose();
        gfx.pose().translate(x + cardW / 2f, y + cardH / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(1.5f));
        gfx.pose().translate(-cardW / 2f, -cardH / 2f, 0);

        // Per-frame HUD — drawPaperCard, экономия ~470 fill-вызовов на кадр.
        PaperRender.drawPaperCard(gfx, 0, 0, cardW, cardH, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, cardW - 12, 6, true);

        gfx.drawString(font, "СОСТОЯНИЕ", 6, 4, PaperRender.INK_FADED, false);

        FoodData food = p.getFoodData();
        int hunger = food.getFoodLevel();
        String hungerLbl = "ГОЛОД " + hunger + "/20";
        gfx.drawString(font, hungerLbl, 6, 14, PaperRender.INK, false);
        PaperRender.drawHatchedBar(gfx, 6, 24, cardW - 12, 7, hunger / 20f, PaperRender.INK);

        int stamY = 36;
        Float staminaRatio = readStamina(p);
        if (staminaRatio != null) {
            String stamLbl = "ВЫНОСЛИВОСТЬ " + Math.round(staminaRatio * 100) + "%";
            int stamColor = staminaRatio < 0.25f ? PaperRender.INK_RED : PaperRender.INK;
            gfx.drawString(font, stamLbl, 6, stamY, stamColor, false);
            PaperRender.drawHatchedBar(gfx, 6, stamY + 10, cardW - 12, 7, staminaRatio, stamColor);
        } else {
            gfx.drawString(font, "(паркур не активен)", 6, stamY, PaperRender.INK_FADED, false);
        }

        gfx.pose().popPose();
    }

    public static void renderStamina(GuiGraphics gfx, int screenW, int screenH) {
    }

    private static boolean parcoolChecked = false;
    private static boolean parcoolAvailable = false;
    private static Method staminaGet;
    private static Method staminaGetValue;
    private static Method staminaGetMaxValue;

    private static void initParcool() {
        if (parcoolChecked) return;
        parcoolChecked = true;
        try {
            Class<?> cls = Class.forName("com.alrex.parcool.api.Stamina");
            staminaGet = cls.getMethod("get", Player.class);
            staminaGetValue = cls.getMethod("getValue");
            staminaGetMaxValue = cls.getMethod("getMaxValue");
            parcoolAvailable = true;
        } catch (Throwable ignored) {
            parcoolAvailable = false;
        }
    }

    private static Float readStamina(Player p) {
        initParcool();
        if (!parcoolAvailable) return null;
        try {
            Object stamina = staminaGet.invoke(null, p);
            if (stamina == null) return null;
            int val = (int) staminaGetValue.invoke(stamina);
            int max = (int) staminaGetMaxValue.invoke(stamina);
            if (max <= 0) return null;
            return Math.max(0f, Math.min(1f, val / (float) max));
        } catch (Throwable t) {
            parcoolAvailable = false;
            return null;
        }
    }
}
