package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class AddLabyrinthScreen extends Screen {
    private final Screen parent;
    private final Consumer<EnterMazeScreen.ServerEntry> onSave;
    private EditBox nameBox;
    private EditBox ipBox;
    private long openedAt = -1L;

    public AddLabyrinthScreen(Screen parent, Consumer<EnterMazeScreen.ServerEntry> onSave) {
        super(Component.literal("ДОБАВИТЬ ЛАБИРИНТ"));
        this.parent = parent;
        this.onSave = onSave;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();

        int paperW = Math.min(this.width - 80, 480);
        int paperH = 240;
        int paperX = (this.width - paperW) / 2;
        int paperY = (this.height - paperH) / 2;

        nameBox = new EditBox(this.font,
                paperX + 24, paperY + 90, paperW - 48, 18,
                Component.literal("имя"));
        nameBox.setBordered(false);
        nameBox.setTextColor(PaperRender.INK);
        nameBox.setMaxLength(40);
        nameBox.setValue("Новый лабиринт");
        addRenderableWidget(nameBox);

        ipBox = new EditBox(this.font,
                paperX + 24, paperY + 138, paperW - 48, 18,
                Component.literal("ip"));
        ipBox.setBordered(false);
        ipBox.setTextColor(PaperRender.INK);
        ipBox.setMaxLength(80);
        addRenderableWidget(ipBox);

        int btnW = 160;
        int btnH = 26;
        int gap = 16;
        int btnsTotal = btnW * 2 + gap;
        int btnsStart = paperX + paperW / 2 - btnsTotal / 2;
        int btnsY = paperY + paperH - 46;

        addRenderableWidget(PaperWidgets.paperButton(
                btnsStart, btnsY, btnW, btnH,
                Component.literal("ДОБАВИТЬ"),
                b -> trySave(),
                0L, PaperRender.INK_RED, null));

        addRenderableWidget(PaperWidgets.paperButton(
                btnsStart + btnW + gap, btnsY, btnW, btnH,
                Component.literal("ОТМЕНА"),
                b -> minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, null));
    }

    private void trySave() {
        String name = nameBox.getValue().trim();
        String ip = ipBox.getValue().trim();

        if (name.isEmpty()) name = "Лабиринт";
        if (ip.isEmpty()) return;

        EnterMazeScreen.ServerEntry entry = new EnterMazeScreen.ServerEntry(name, ip, "Пользовательский узел");

        if (onSave != null) {
            onSave.accept(entry);
        }

        // Возвращаемся на экран входа
        minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        long age = Math.max(0, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 280f));

        int paperW = Math.min(this.width - 80, 480);
        int paperH = 240;
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

        Font font = this.font;

        String kicker = "ФАЙЛ №02 · РЕГИСТРАЦИЯ НОВОГО УЗЛА";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, paperW / 2 - kw / 2, 10,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        String title = "НОВЫЙ ЛАБИРИНТ";
        float ts = 2.0f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - tw / 2f, 22, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        int lineY = 22 + (int) (9 * ts) + 6;
        PaperRender.drawHandDivider(gfx, paperW / 2 - 110, lineY, 220,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));

        gfx.drawString(font, "имя лабиринта:", 24, 76, PaperRender.INK_SOFT, false);
        gfx.drawString(font, "адрес (IP:порт):", 24, 124, PaperRender.INK_SOFT, false);

        gfx.fill(24, 90 + 16, paperW - 24, 90 + 17, PaperRender.INK);
        gfx.fill(24, 138 + 16, paperW - 24, 138 + 17, PaperRender.INK);

        gfx.drawString(font, "пример: 127.0.0.1:25565", 24, 138 + 22,
                PaperRender.INK_FADED, false);

        gfx.pose().popPose();

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}