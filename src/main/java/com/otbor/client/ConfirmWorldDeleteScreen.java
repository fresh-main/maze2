package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class ConfirmWorldDeleteScreen extends Screen {
    private final Screen parent;
    private final LevelSummary summary;
    private long openedAt = -1L;

    protected ConfirmWorldDeleteScreen(Screen parent, LevelSummary summary) {
        super(Component.literal("УДАЛИТЬ МИР?"));
        this.parent = parent;
        this.summary = summary;
    }

    public static Screen create(Screen parent, LevelSummary summary) {
        return new ConfirmWorldDeleteScreen(parent, summary);
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();

        int paperW = Math.min(this.width - 80, 460);
        int paperH = 180;
        int paperX = (this.width - paperW) / 2;
        int paperY = (this.height - paperH) / 2;

        int btnW = 140;
        int btnH = 26;
        int gap = 20;
        int btnsTotal = btnW * 2 + gap;
        int btnsStart = paperX + paperW / 2 - btnsTotal / 2;
        int btnsY = paperY + paperH - 50;

        addRenderableWidget(PaperWidgets.paperButton(
                btnsStart, btnsY, btnW, btnH,
                Component.literal("УДАЛИТЬ"),
                b -> {
                    // В 1.20.1 удаление происходит через LevelStorageAccess
                    try (LevelStorageSource.LevelStorageAccess access = minecraft.getLevelSource().createAccess(summary.getLevelId())) {
                        access.deleteLevel();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    minecraft.setScreen(new SelectWorldScreen(parent));
                },
                0L, PaperRender.INK_RED, null));

        addRenderableWidget(PaperWidgets.paperButton(
                btnsStart + btnW + gap, btnsY, btnW, btnH,
                Component.literal("ОТМЕНА"),
                b -> minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, null));
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        long age = Math.max(0, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 260f));

        int paperW = Math.min(this.width - 80, 460);
        int paperH = 180;
        int paperX = (this.width - paperW) / 2;
        int paperY = (this.height - paperH) / 2;
        int slide = (int) ((1f - appear) * 14f);

        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW / 2f, paperY + paperH / 2f + slide, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.6f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, appear, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 12, 8, false);
        PaperRender.drawPin(gfx, paperW - 12, 8, true);

        var font = this.font;

        String title = "ПОДТВЕРЖДЕНИЕ";
        float ts = 2.0f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - tw / 2f, 18, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        int lineY = 18 + (int) (9 * ts) + 8;
        PaperRender.drawHandDivider(gfx, paperW / 2 - 100, lineY, 200,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));

        String worldName = summary.getLevelName();
        if (worldName.isEmpty()) worldName = "Безымянный мир";

        String confirmText = "Вы уверены, что хотите удалить мир?";
        gfx.drawString(font, confirmText, paperW / 2 - font.width(confirmText) / 2, lineY + 14,
                PaperRender.INK, false);

        String quotedName = "\"" + worldName + "\"";
        gfx.drawString(font, quotedName, paperW / 2 - font.width(quotedName) / 2, lineY + 28,
                PaperRender.INK_RED, false);

        String warningText = "Это действие нельзя отменить!";
        gfx.drawString(font, warningText, paperW / 2 - font.width(warningText) / 2, lineY + 44,
                PaperRender.INK_FADED, false);

        gfx.pose().popPose();

        super.render(gfx, mouseX, mouseY, partialTick);
    }
}