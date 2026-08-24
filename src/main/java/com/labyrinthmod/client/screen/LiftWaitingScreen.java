package com.labyrinthmod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class LiftWaitingScreen extends Screen {

    public LiftWaitingScreen() {
        super(Component.literal("Lift Lock"));
    }

    @Override
    protected void init() {
        super.init();
        // Кнопка "ОТМЕНА" (Разрывает соединение и возвращает в главное меню)
        int btnW = 140;
        int btnH = 24;
        int btnX = (this.width - btnW) / 2;
        int btnY = (this.height / 2) + 80;

        this.addRenderableWidget(Button.builder(Component.literal("ОТМЕНА"), (b) -> {
            if (this.minecraft != null && this.minecraft.getConnection() != null) {
                this.minecraft.getConnection().getConnection().disconnect(Component.literal("Ритуал отменен игроком"));
            } else if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
        }).bounds(btnX, btnY, btnW, btnH).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // ★ ФОН "ДОСКА" ★
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        int paperW = Math.min(this.width - 80, 420);
        int paperH = Math.min(this.height - 60, 260);
        int px = (this.width - paperW) / 2;
        int py = (this.height - paperH) / 2;

        // ★ БУМАГА ★
        PaperRender.drawPaper(gfx, px, py, paperW, paperH, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, px + 16, py + 12, false);
        PaperRender.drawPin(gfx, px + paperW - 16, py + 12, true);
        PaperRender.drawTape(gfx, px + 12, py - 6, 58, 12, 0xC0);
        PaperRender.drawTape(gfx, px + paperW - 70, py - 6, 58, 12, 0xC0);

        // ★ ТЕКСТ ★
        String kicker = "О.Т.Б.О.Р · СТАТУС · ДЕЛО №047-Л";
        int kickerW = this.font.width(kicker);
        gfx.drawString(this.font, kicker, px + paperW / 2 - kickerW / 2, py + 12, PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        String title = "ЛИФТ НЕ ГОТОВ";
        float titleScale = 2.0f;
        int titleW = (int) (this.font.width(title) * titleScale);
        int titleY = py + 35;

        gfx.pose().pushPose();
        gfx.pose().translate(px + paperW / 2f - titleW / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        PaperRender.drawInkText(gfx, this.font, title, 0, 0, PaperRender.INK_RED);
        gfx.pose().popPose();

        String sub = "Р И Т У А Л   А К Т И В А Ц И И   И Д Е Т";
        int subW = this.font.width(sub);
        gfx.drawString(this.font, sub, px + paperW / 2 - subW / 2, titleY + 25, PaperRender.INK_SOFT, false);

        // Разделитель
        int lineY = titleY + 45;
        int lineW = paperW - 80;
        PaperRender.drawInkStroke(gfx, px + paperW / 2 - lineW / 2, lineY, lineW, 1, PaperRender.withAlpha(PaperRender.INK_RED, 0.6f), 7L);

        // Описание
        String desc1 = "Подождите завершения калибровки механизмов.";
        String desc2 = "Как только лифт будет готов, вы автоматически";
        String desc3 = "сможете продолжить погружение.";

        gfx.drawString(this.font, desc1, px + paperW / 2 - this.font.width(desc1) / 2, lineY + 15, PaperRender.INK, false);
        gfx.drawString(this.font, desc2, px + paperW / 2 - this.font.width(desc2) / 2, lineY + 28, PaperRender.INK_FADED, false);
        gfx.drawString(this.font, desc3, px + paperW / 2 - this.font.width(desc3) / 2, lineY + 41, PaperRender.INK_FADED, false);

        // Штампы
        gfx.pose().pushPose();
        gfx.pose().translate(px + paperW - 90, py + paperH - 60, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12f));
        gfx.pose().scale(1.2f, 1.2f, 1f);
        PaperRender.drawRectStamp(gfx, this.font, "ОЖИДАНИЕ", 0, 0, (0xDD << 24) | (PaperRender.INK_RED & 0x00FFFFFF));
        gfx.pose().popPose();

        // Рендер кнопок
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Запрещаем закрывать экран по ESC
    }
}