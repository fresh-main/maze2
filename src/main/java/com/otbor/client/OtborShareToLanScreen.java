package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTabControl;
import org.jetbrains.annotations.NotNull;

public class OtborShareToLanScreen extends Screen {
    private final Screen parent;
    private GameType gameMode;
    private boolean allowCheats;
    private int port;
    private long openedAt = -1L;

    // Поле ввода порта
    private EditBox portBox;
    private static final int PORT_LOWER_BOUND = 1024;
    private static final int PORT_HIGHER_BOUND = 65535;

    // Координаты бумаги
    private int paperX, paperY, paperW, paperH;
    // Координаты интерактивных опций (абсолютные, без поворота)
    private int optX, optY;
    private int portBoxX, portBoxY, portBoxW, portBoxH;

    public OtborShareToLanScreen(Screen parent) {
        super(Component.literal("ОТКРЫТЬ ДЛЯ СЕТИ"));
        this.parent = parent;
        var server = Minecraft.getInstance().getSingleplayerServer();
        this.gameMode = server != null ? server.getDefaultGameType() : GameType.SURVIVAL;
        this.allowCheats = server != null && server.getWorldData().getAllowCommands();
        this.port = HttpUtil.getAvailablePort();
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();

        paperW = Math.min(this.width - 80, 480);
        paperH = 330; // ★ Увеличили высоту для поля порта ★
        paperX = (this.width - paperW) / 2;
        paperY = (this.height - paperH) / 2;

        // ★ ЦЕНТРИРОВАНИЕ ПЕРЕКЛЮЧАТЕЛЕЙ ★
        int toggleW = 260;
        optX = paperX + paperW / 2 - toggleW / 2;
        optY = paperY + 85;

        // ★ ПОЛЕ ВВОДА ПОРТА ★
        portBoxW = 140;
        portBoxH = 20;
        portBoxX = paperX + paperW / 2 - portBoxW / 2;
        portBoxY = optY + 115;

        portBox = new EditBox(this.font, portBoxX, portBoxY, portBoxW, portBoxH, Component.literal("порт"));
        portBox.setBordered(false);
        portBox.setTextColor(PaperRender.INK);
        portBox.setMaxLength(5);
        portBox.setValue(String.valueOf(this.port));
        portBox.setResponder(this::tryParsePort);
        addRenderableWidget(portBox);

        int btnW = 160;
        int btnH = 26;
        int gap = 20;
        int btnsTotal = btnW * 2 + gap;
        int btnsStart = paperX + paperW / 2 - btnsTotal / 2;
        int btnsY = paperY + paperH - 50;

        addRenderableWidget(PaperWidgets.paperButton(
                btnsStart, btnsY, btnW, btnH,
                Component.literal("▶ ОТКРЫТЬ СЕТЬ"),
                b -> openToLan(),
                0L, PaperRender.INK_RED, null));

        addRenderableWidget(PaperWidgets.paperButton(
                btnsStart + btnW + gap, btnsY, btnW, btnH,
                Component.literal("✖ ОТМЕНА"),
                b -> minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, null));
    }

    @Override
    public void tick() {
        super.tick();
        if (portBox != null) {
            portBox.tick();
        }
    }

    private void tryParsePort(String text) {
        if (text.isBlank()) {
            this.port = HttpUtil.getAvailablePort();
            portBox.setTextColor(PaperRender.INK);
            return;
        }
        try {
            int p = Integer.parseInt(text);
            if (p >= PORT_LOWER_BOUND && p <= PORT_HIGHER_BOUND) {
                this.port = p;
                portBox.setTextColor(PaperRender.INK);
            } else {
                portBox.setTextColor(PaperRender.INK_RED);
            }
        } catch (NumberFormatException e) {
            portBox.setTextColor(PaperRender.INK_RED);
        }
    }

    private void openToLan() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            try {
                boolean success = server.publishServer(this.gameMode, this.allowCheats, this.port);
                if (success) {
                    Component msg = net.minecraft.server.commands.PublishCommand.getSuccessMessage(this.port);
                    this.minecraft.gui.getChat().addMessage(msg);
                    this.minecraft.updateTitle();
                    this.minecraft.setScreen(this.parent);
                } else {
                    Component msg = Component.translatable("commands.publish.failed");
                    this.minecraft.gui.getChat().addMessage(msg);
                    this.minecraft.setScreen(this.parent);
                }
            } catch (Exception e) {
                e.printStackTrace();
                this.minecraft.setScreen(this.parent);
            }
        } else {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        long age = Math.max(0, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 280f));
        int slide = (int) ((1f - appear) * 14f);

        // 1. Рисуем бумажную подложку с лёгким поворотом и анимацией появления
        gfx.pose().pushPose();
        gfx.pose().translate(paperX + paperW / 2f, paperY + paperH / 2f + slide, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.6f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, appear, PaperRender.PAPER_LIGHT);

        // ★ ДЕКОР: Скотч и булавки ★
        PaperRender.drawTape(gfx, paperW / 2 - 40, -6, 80, 14, 0xB0);
        PaperRender.drawPin(gfx, 14, 10, false);
        PaperRender.drawPin(gfx, paperW - 14, 10, true);

        // ★ ДЕКОР: Дырки от булавок ★
        if (age > 800) {
            gfx.fill(40, 60, 43, 63, 0xFF2A1A0E);
            gfx.fill(41, 61, 42, 62, 0xFF000000);
            gfx.fill(paperW - 45, paperH - 50, paperW - 42, paperH - 47, 0xFF2A1A0E);
            gfx.fill(paperW - 44, paperH - 49, paperW - 43, paperH - 48, 0xFF000000);
        }

        // ★ ДЕКОР: Чернильные кляксы ★
        if (age > 500) {
            int alpha = (int) (0x44 * Math.min(1f, (age - 500) / 1000f));
            int blotCol = (alpha << 24) | (PaperRender.INK_DARK & 0x00FFFFFF);
            gfx.fill(paperW - 60, 40, paperW - 52, 46, blotCol);
            gfx.fill(paperW - 55, 45, paperW - 48, 48, blotCol);
            gfx.fill(30, paperH - 80, 38, paperH - 74, blotCol);
        }

        Font font = this.font;

        String kicker = "ФАЙЛ №06 · ПРОТОКОЛ СЕТЕВОГО ДОСТУПА";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, paperW / 2 - kw / 2, 12,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        String title = "ОТКРЫТЬ ДЛЯ СЕТИ";
        float ts = 2.0f;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - tw / 2f, 24, 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        int lineY = 24 + (int) (9 * ts) + 6;
        PaperRender.drawHandDivider(gfx, paperW / 2 - 120, lineY, 240,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.7f));

        // ★ ДЕКОР: Каракули "внимание" ★
        gfx.pose().pushPose();
        gfx.pose().translate(paperW - 130, paperH - 90, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4f));
        PaperRender.drawScribble(gfx, font, "внимание:", 0, 0, PaperRender.withAlpha(PaperRender.INK_RED, 0.6f));
        PaperRender.drawScribble(gfx, font, "любой в сети", 0, 10, PaperRender.withAlpha(PaperRender.INK_RED, 0.6f));
        PaperRender.drawScribble(gfx, font, "сможет войти.", 0, 20, PaperRender.withAlpha(PaperRender.INK_RED, 0.6f));
        gfx.pose().popPose();

        // ★ ДЕКОР: Штамп "СЕТЕВОЙ УЗЕЛ" ★
        float pulse = 0.5f + 0.5f * net.minecraft.util.Mth.sin(PaperRender.gameTime() * 0.07f);
        gfx.pose().pushPose();
        gfx.pose().translate(60, paperH - 45, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8f));
        PaperRender.drawRoundStamp(gfx, font, 0, 0, 24, "СЕТЕВОЙ", "УЗЕЛ",
                PaperRender.withAlpha(PaperRender.INK_RED, 0.3f + 0.2f * pulse));
        gfx.pose().popPose();

        gfx.pose().popPose(); // ★ Закрываем общий pushPose бумаги ★

        // 2. Рисуем интерактивные опции БЕЗ поворота (отцентрованы)
        gfx.drawString(font, "РЕЖИМ ВЗАИМОДЕЙСТВИЯ:", optX, optY, PaperRender.INK_DARK, false);
        drawCycleOption(gfx, font, optX, optY + 14, getGameModeName(), mouseX, mouseY);

        gfx.drawString(font, "ДОПУСК КОМАНД:", optX, optY + 55, PaperRender.INK_DARK, false);
        drawCycleOption(gfx, font, optX, optY + 69, allowCheats ? "РАЗРЕШЕНО" : "ЗАПРЕЩЕНО", mouseX, mouseY);

        // 3. Рисуем поле ввода порта
        gfx.drawString(font, "НОМЕР ПОРТА (1024-65535):", portBoxX, portBoxY - 12, PaperRender.INK_FADED, false);

        // Рамка для поля ввода (стилизованная под рукописный бланк)
        gfx.fill(portBoxX, portBoxY + portBoxH, portBoxX + portBoxW, portBoxY + portBoxH + 1, PaperRender.INK);
        gfx.fill(portBoxX - 2, portBoxY + portBoxH + 1, portBoxX + 2, portBoxY + portBoxH + 2, PaperRender.INK); // засечка слева
        gfx.fill(portBoxX + portBoxW - 2, portBoxY + portBoxH + 1, portBoxX + portBoxW + 2, portBoxY + portBoxH + 2, PaperRender.INK); // засечка справа

        // Рендерим сам EditBox
        portBox.render(gfx, mouseX, mouseY, partialTick);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawCycleOption(GuiGraphics gfx, Font font, int x, int y, String value, int mouseX, int mouseY) {
        int boxW = 260; // ★ Увеличена ширина для центрирования ★
        int boxH = 22;

        gfx.fill(x, y, x + boxW, y + boxH, PaperRender.PAPER_BASE);
        gfx.fill(x, y, x + boxW, y + 1, PaperRender.INK_SOFT);
        gfx.fill(x, y + boxH - 1, x + boxW, y + boxH, PaperRender.INK_SOFT);
        gfx.fill(x, y, x + 1, y + boxH, PaperRender.INK_SOFT);
        gfx.fill(x + boxW - 1, y, x + boxW, y + boxH, PaperRender.INK_SOFT);

        gfx.fill(x + 28, y, x + 29, y + boxH, PaperRender.INK_FADED);
        gfx.fill(x + boxW - 29, y, x + boxW - 28, y + boxH, PaperRender.INK_FADED);

        boolean hoverLeft = isInRect(mouseX, mouseY, x, y, 28, boxH);
        gfx.drawString(font, "  <  ", x + 10, y + 7, hoverLeft ? PaperRender.INK_RED : PaperRender.INK_SOFT, false);

        gfx.drawString(font, value, x + boxW / 2 - font.width(value) / 2, y + 7, PaperRender.INK_RED, false);

        boolean hoverRight = isInRect(mouseX, mouseY, x + boxW - 28, y, 28, boxH);
        gfx.drawString(font, "  >  ", x + boxW - 18, y + 7, hoverRight ? PaperRender.INK_RED : PaperRender.INK_SOFT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Обработка кликов по переключателям
        if (handleCycleClick(mouseX, mouseY, optX, optY + 14)) {
            cycleGameMode();
            return true;
        }
        if (handleCycleClick(mouseX, mouseY, optX, optY + 69)) {
            allowCheats = !allowCheats;
            return true;
        }

        // Передаем остальные клики (включая клик по EditBox) в супер-класс
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleCycleClick(double mx, double my, int x, int y) {
        return isInRect(mx, my, x, y, 260, 22);
    }

    private void cycleGameMode() {
        if (gameMode == GameType.SURVIVAL) gameMode = GameType.CREATIVE;
        else if (gameMode == GameType.CREATIVE) gameMode = GameType.ADVENTURE;
        else gameMode = GameType.SURVIVAL;
    }

    private String getGameModeName() {
        if (gameMode == GameType.SURVIVAL) return "ВЫЖИВАНИЕ";
        if (gameMode == GameType.CREATIVE) return "ТВОРЧЕСТВО";
        return "ПРИКЛЮЧЕНИЕ";
    }

    private boolean isInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
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