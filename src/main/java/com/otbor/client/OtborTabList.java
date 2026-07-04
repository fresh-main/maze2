package com.otbor.client;
import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
public final class OtborTabList {
    private OtborTabList() {}

    public static void render(GuiGraphics gfx, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;

        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0.0) guiScale = 2.0;
        float sm = (float) ((int) Math.round(guiScale)) / 2.0f;

        int count = mc.player.connection.getListedOnlinePlayers().size();

        Font font = mc.font;
        int cardW = (int)(220 * sm);
        int cardH = (int)(64 * sm);
        int cardX = screenW / 2 - cardW / 2;
        int cardY = (int)(10 * sm);

        PaperRender.drawPaperCard(gfx, cardX, cardY, cardW, cardH, 1.0f, PaperRender.PAPER_BASE);
        PaperRender.drawPin(gfx, cardX + (int)(10 * sm), cardY + (int)(8 * sm), false);
        PaperRender.drawPin(gfx, cardX + cardW - (int)(10 * sm), cardY + (int)(8 * sm), true);

        String kicker = "ПЕРЕКЛИЧКА · ЛАБИРИНТ ";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, cardX + cardW / 2 - kw / 2, cardY + (int)(6 * sm),
                PaperRender.INK_FADED, false);

        String num = String.valueOf(count);
        gfx.pose().pushPose();
        float ns = 3.0f * sm;
        int nw = (int) (font.width(num) * ns);
        gfx.pose().scale(ns, ns, 1f);
        gfx.drawString(font,
                num,
                (int) ((cardX + cardW / 2 - nw / 2) / ns),
                (int) ((cardY + (int)(18 * sm)) / ns),
                PaperRender.INK, false);
        gfx.pose().popPose();

        String caption = count == 1 ? "бегущий в лабиринте " : "бегущих в лабиринте ";
        int cw = font.width(caption);
        gfx.drawString(font, caption, cardX + cardW / 2 - cw / 2, cardY + (int)(48 * sm),
                PaperRender.INK_SOFT, false);

        var server = mc.getCurrentServer();
        String sub = server != null ? server.name : (mc.hasSingleplayerServer() ? "локальный мир " : "— ");
        int sw = font.width(sub);
        gfx.drawString(font, sub, cardX + cardW / 2 - sw / 2, cardY + cardH + (int)(4 * sm),
                PaperRender.INK_FADED, false);
    }
}