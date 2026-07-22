package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class DirectConnectMazeScreen extends Screen {
    private final Screen parent;
    private EditBox ipEditBox;

    private int paperX, paperY, paperW, paperH;
    private int btnEnterX, btnEnterY, btnEnterW, btnEnterH;
    private int btnCancelX, btnCancelY, btnCancelW, btnCancelH;
    private boolean hoverEnter = false, hoverCancel = false;

    public DirectConnectMazeScreen(Screen parent) {
        super(Component.literal("Прямое подключение"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        // Размеры и позиция центральной "бумаги"
        paperW = 340;
        paperH = 220;
        paperX = (this.width - paperW) / 2;
        paperY = (this.height - paperH) / 2;

        // Поле ввода IP (в абсолютных координатах, чтобы не ломалось при повороте)
        int boxW = paperW - 60;
        int boxH = 18;
        int boxX = paperX + 30;
        int boxY = paperY + 90;

        ipEditBox = new EditBox(this.font, boxX, boxY, boxW, boxH, Component.literal("IP адрес"));
        ipEditBox.setBordered(false); // Убираем стандартную рамку, будем рисовать свою линию
        ipEditBox.setTextColor(PaperRender.INK);
        ipEditBox.setMaxLength(64);
        ipEditBox.setValue("127.0.0.1:25565");
        ipEditBox.setFocused(true);
        this.addRenderableWidget(ipEditBox);

        // Кнопки (в абсолютных координатах)
        btnEnterW = 130;
        btnEnterH = 26;
        btnEnterX = paperX + 30;
        btnEnterY = paperY + paperH - 50;

        btnCancelW = 130;
        btnCancelH = 26;
        btnCancelX = paperX + paperW - 30 - btnCancelW;
        btnCancelY = btnEnterY;
    }

    private void connectToServer() {
        String ip = ipEditBox.getValue().trim();
        if (ip.isEmpty()) return;

        ServerData serverData = new ServerData("Прямое подключение", ip, false);
        ConnectScreen.startConnecting(parent, this.minecraft, ServerAddress.parseString(ip), serverData, false);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // 1. Рисуем темный фон доски
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        Font font = this.font;

        // 2. Рисуем бумагу с легким наклоном (как в EnterMazeScreen)
        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW / 2f, paperY + paperH / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-1.5f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, 1.0f, PaperRender.PAPER_BASE);
        PaperRender.drawPin(gfx, 20, 14, false);
        PaperRender.drawPin(gfx, paperW - 20, 14, true);

        String kicker = "ПРОТОКОЛ · ПРЯМОЙ ВХОД";
        gfx.drawString(font, kicker, 20, 16, PaperRender.INK_FADED, false);

        String title = "ВВОД КООРДИНАТ";
        float ts = 1.8f;
        gfx.pose().pushPose();
        gfx.pose().translate(20, 28, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        PaperRender.drawHandDivider(gfx, 20, 28 + (int)(9*ts) + 8, paperW - 40, PaperRender.withAlpha(PaperRender.INK_SOFT, 0.8f));

        gfx.drawString(font, "укажи точку входа в лабиринт:", 30, 70, PaperRender.INK_SOFT, false);
        gfx.drawString(font, "формат: ip:port (например, 127.0.0.1:25565)", 30, 118, PaperRender.INK_FADED, false);

        gfx.pose().popPose(); // Закрываем поворот бумаги

        // 3. Рисуем EditBox и кнопки в абсолютных координатах (без поворота, чтобы клики работали идеально)
        ipEditBox.render(gfx, mouseX, mouseY, partialTick);
        // Рисуем линию подчеркивания для поля ввода
        gfx.fill(paperX + 30, paperY + 108, paperX + paperW - 30, paperY + 109, PaperRender.INK);

        renderCustomButton(gfx, btnEnterX, btnEnterY, btnEnterW, btnEnterH, "-> ВОЙТИ <-", hoverEnter, PaperRender.INK_RED);
        renderCustomButton(gfx, btnCancelX, btnCancelY, btnCancelW, btnCancelH, "<- ОТМЕНА", hoverCancel, PaperRender.INK_SOFT);

        // Обновляем состояние наведения
        hoverEnter = mouseX >= btnEnterX && mouseX <= btnEnterX + btnEnterW && mouseY >= btnEnterY && mouseY <= btnEnterY + btnEnterH;
        hoverCancel = mouseX >= btnCancelX && mouseX <= btnCancelX + btnCancelW && mouseY >= btnCancelY && mouseY <= btnCancelY + btnCancelH;
    }

    private void renderCustomButton(GuiGraphics gfx, int x, int y, int w, int h, String text, boolean hovered, int borderColor) {
        int bg = hovered ? PaperRender.PAPER_BASE : PaperRender.PAPER_DARK;
        int border = hovered ? PaperRender.INK_RED : borderColor;

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        Font font = this.font;
        int tw = font.width(text);
        int color = hovered ? PaperRender.INK_RED : PaperRender.INK;
        gfx.drawString(font, text, x + w / 2 - tw / 2, y + (h - 8) / 2, color, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hoverEnter) {
            connectToServer();
            return true;
        }
        if (hoverCancel) {
            this.minecraft.setScreen(parent);
            return true;
        }
        if (ipEditBox.isMouseOver(mx, my)) {
            ipEditBox.setFocused(true);
            ipEditBox.mouseClicked(mx, my, button);
            return true;
        } else {
            ipEditBox.setFocused(false);
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.minecraft.setScreen(parent);
            return true;
        }
        if (ipEditBox.isFocused() && keyCode == 257) { // ENTER
            connectToServer();
            return true;
        }
        if (ipEditBox.isFocused()) {
            return ipEditBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (ipEditBox.isFocused()) {
            return ipEditBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}