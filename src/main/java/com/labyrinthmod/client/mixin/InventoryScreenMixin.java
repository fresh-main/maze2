package com.labyrinthmod.client.mixin;

import com.otbor.client.EffectsNote;
import com.otbor.client.widgets.PaperContainerRender;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    private static final int SCREEN_W = 220;
    private static final int SCREEN_H = 200;

    private static final int AVATAR_FRAME_X = 36;
    private static final int AVATAR_FRAME_Y = 22;
    private static final int AVATAR_FRAME_W = 90;
    private static final int AVATAR_FRAME_H = 140;

    private static final int HOTBAR_Y  = 175;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void otbor$resize(CallbackInfo ci) {
        this.imageWidth  = SCREEN_W;
        this.imageHeight = SCREEN_H;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void otbor$hideLabels(CallbackInfo ci) {
        this.titleLabelY = -9999;
        this.inventoryLabelY = -9999;
        this.children().removeIf(c -> c instanceof net.minecraft.client.gui.components.ImageButton);
        this.renderables.removeIf(r -> r instanceof net.minecraft.client.gui.components.ImageButton);
        this.leftPos = (this.width - this.imageWidth) / 2;
    }

    @Redirect(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V"
            )
    )
    private void otbor$relocatePlayer(GuiGraphics gfx, int px, int py, int scale,
                                      float mx, float my, LivingEntity entity) {
        int cx = this.leftPos + AVATAR_FRAME_X + AVATAR_FRAME_W / 2;
        int cy = this.topPos  + AVATAR_FRAME_Y + AVATAR_FRAME_H - 36;
        InventoryScreen.renderEntityInInventoryFollowsMouse(gfx, cx, cy, 40, mx, my, entity);
    }

    @Redirect(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"
            )
    )
    private void otbor$paperBackground(GuiGraphics gfx, ResourceLocation loc,
                                       int x, int y, int u, int v, int w, int h) {
        Font font = Minecraft.getInstance().font;

        // === Animation progress (общий tracker экрана) ===
        float t = PaperContainerRender.animProgress(this);

        // 1. Бумага сама по себе — без alpha-fade (она кривая по краям, fade ломает borders).
        PaperRender.drawPaper(gfx, x, y, SCREEN_W, SCREEN_H, 1.0f, PaperRender.PAPER_LIGHT);

        // 2. Перо обводит лист по периметру (30..110 мс)
        float borderTrace = PaperContainerRender.phase(t, 0.12f, 0.44f);
        if (borderTrace > 0f && borderTrace < 1f) {
            PaperContainerRender.tracePerimeter(gfx, x + 4, y + 4,
                    SCREEN_W - 8, SCREEN_H - 8,
                    borderTrace, PaperRender.INK, 1);
        }

        // 3. Булавки появляются после старта обводки
        if (t > 0.18f) {
            PaperRender.drawPin(gfx, x + 10, y + 8, false);
            PaperRender.drawPin(gfx, x + SCREEN_W - 10, y + 8, true);
        }

        // 4. Кикер и заголовок — typewriter
        float kickProg = PaperContainerRender.phase(t, 0.20f, 0.42f);
        if (kickProg > 0f) {
            String kick = "ЭКИПИРОВКА · БЕГУЩИЙ";
            int kw = font.width(kick);
            PaperContainerRender.typewriter(gfx, font, kick,
                    x + SCREEN_W / 2 - kw / 2, y - 12,
                    PaperRender.INK_FADED, kickProg);
        }

        // 5. Рамка фото — обводится пером (одновременно с border общего листа)
        float frameProg = PaperContainerRender.phase(t, 0.28f, 0.56f);
        if (frameProg > 0f) {
            drawPhotoFrame(gfx, x + AVATAR_FRAME_X, y + AVATAR_FRAME_Y,
                    AVATAR_FRAME_W, AVATAR_FRAME_H, frameProg);
        }

        // 6. Подпись «БЕГУЩИЙ №47» — typewriter
        float noProg = PaperContainerRender.phase(t, 0.38f, 0.58f);
        if (noProg > 0f) {
            String no = "БЕГУЩИЙ №47";
            int nw = font.width(no);
            PaperContainerRender.typewriter(gfx, font, no,
                    x + AVATAR_FRAME_X + AVATAR_FRAME_W / 2 - nw / 2,
                    y + AVATAR_FRAME_Y + 3,
                    PaperRender.INK_FADED, noProg);
        }

        // 7. Подзаголовок «ХОТБАР» — typewriter
        float hbProg = PaperContainerRender.phase(t, 0.48f, 0.68f);
        if (hbProg > 0f) {
            String hb = "ХОТБАР · ЧТО В РУКАХ";
            int hbw = font.width(hb);
            PaperContainerRender.typewriter(gfx, font, hb,
                    x + SCREEN_W / 2 - hbw / 2,
                    y + HOTBAR_Y - 12,
                    PaperRender.INK_FADED, hbProg);
            // тонкая линия над хотбаром
            int divW = (int) ((SCREEN_W - 20) * hbProg);
            gfx.fill(x + 10, y + HOTBAR_Y - 4, x + 10 + divW, y + HOTBAR_Y - 3,
                    PaperRender.withAlpha(PaperRender.INK_SOFT, 0.4f));
        }

        // 8. Слоты — скетчатся волной слева-сверху → справа-снизу
        Slot[] sortedSlots = this.getMenu().slots.stream()
                .filter(s -> s.isActive() && s.x >= 0 && s.y >= 0)
                .sorted((a, b) -> {
                    int dy = Integer.compare(a.y, b.y);
                    return dy != 0 ? dy : Integer.compare(a.x, b.x);
                })
                .toArray(Slot[]::new);
        float slotsStart = 0.40f;
        float slotsEnd = 0.92f;
        float slotSpacing = sortedSlots.length == 0
                ? 0f
                : Math.min(0.04f, (slotsEnd - slotsStart) / sortedSlots.length);
        for (int i = 0; i < sortedSlots.length; i++) {
            Slot slot = sortedSlots[i];
            float st = slotsStart + i * slotSpacing;
            float local = PaperContainerRender.phase(t, st, st + 0.12f);
            PaperContainerRender.sketchSlotBox(gfx, x + slot.x, y + slot.y, local);
        }

        // 9. Штамп — прихлопывается с overshoot
        float stampProg = PaperContainerRender.phase(t, 0.78f, 0.92f);
        if (stampProg > 0f) {
            float ease = PaperContainerRender.easeBackOut(stampProg);
            float scale = 1.6f - 0.6f * ease;
            int alpha = (int) (255 * Math.min(1f, stampProg * 1.6f));
            int stampColor = (alpha << 24) | (PaperRender.INK_RED & 0xFFFFFF);

            gfx.pose().pushPose();
            gfx.pose().translate(x + SCREEN_W - 40, y + SCREEN_H + 8, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6f));
            gfx.pose().scale(scale, scale, 1f);
            PaperRender.drawRectStamp(gfx, font, "ОПИСЬ ТЕЛА", 0, 0, stampColor);
            gfx.pose().popPose();
        }

        // 10. Боковой блок «АНОМАЛИИ · СТАТУС» (EffectsNote) — рисуется после всего
        if (t > 0.6f) {
            EffectsNote.draw(gfx, x - EffectsNote.OFFSET_X, y + EffectsNote.OFFSET_Y);
        }
    }

    /**
     * Рамка-фото с углами-«усиками». Обводится пером по периметру синхронно с {@code progress}.
     */
    private static void drawPhotoFrame(GuiGraphics gfx, int x, int y, int w, int h, float progress) {
        if (progress <= 0f) return;

        // тёмная подложка появляется сразу
        if (progress > 0.05f) {
            int a = (int) (0x30 * Math.min(1f, progress * 4f));
            gfx.fill(x, y, x + w, y + h, (a << 24));
        }

        // обводка пером по периметру
        PaperContainerRender.tracePerimeter(gfx, x, y, w, h, progress, PaperRender.INK, 1);

        // углы-усики появляются под конец
        if (progress > 0.85f) {
            float cp = (progress - 0.85f) / 0.15f;
            int alpha = (int) (255 * Math.min(1f, cp));
            int col = (alpha << 24) | (PaperRender.INK_SOFT & 0xFFFFFF);
            gfx.fill(x + 1, y + 1, x + 4, y + 2, col);
            gfx.fill(x + 1, y + 1, x + 2, y + 4, col);
            gfx.fill(x + w - 4, y + 1, x + w - 1, y + 2, col);
            gfx.fill(x + w - 2, y + 1, x + w - 1, y + 4, col);
            gfx.fill(x + 1, y + h - 2, x + 4, y + h - 1, col);
            gfx.fill(x + 1, y + h - 4, x + 2, y + h - 1, col);
            gfx.fill(x + w - 4, y + h - 2, x + w - 1, y + h - 1, col);
            gfx.fill(x + w - 2, y + h - 4, x + w - 1, y + h - 1, col);
        }
    }
}
