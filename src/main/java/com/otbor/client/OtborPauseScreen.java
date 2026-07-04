package com.otbor.client;
import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
public class OtborPauseScreen extends Screen {
    private final boolean showMenu;
    private long openedAt = -1L;
    private float scaleMultiplier = 1.0f;

    public OtborPauseScreen(boolean showMenu) {
        super(showMenu ? Component.literal("ПАУЗА ") : Component.literal("СОХРАНЕНИЕ... "));
        this.showMenu = showMenu;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();

        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale <= 0.0) guiScale = 2.0;
        scaleMultiplier = (float) ((int) Math.round(guiScale)) / 2.0f;

        if (!showMenu) return;

        int cx = this.width / 2;
        int btnW = Math.min((int)(280 * scaleMultiplier), this.width - (int)(80 * scaleMultiplier));
        int btnH = (int)(26 * scaleMultiplier);
        int gap = (int)(10 * scaleMultiplier);

        boolean singleplayer = this.minecraft.hasSingleplayerServer() && this.minecraft.getSingleplayerServer() != null;
        boolean canPublish = singleplayer && !this.minecraft.getSingleplayerServer().isPublished();
        int count = 3 + (canPublish ? 1 : 0);
        int totalH = btnH * count + gap * (count - 1);
        int startY = this.height / 2 - totalH / 2 + (int)(10 * scaleMultiplier);

        long delay = 80L;
        int row = 0;

        addRenderableWidget(PaperWidgets.paperButton(
                cx - btnW / 2, startY + (btnH + gap) * row++,
                btnW, btnH,
                Component.literal("▶ ВЕРНУТЬСЯ В ИГРУ "),
                b -> resumeGame(),
                delay * 0, PaperRender.INK_RED, null));

        addRenderableWidget(PaperWidgets.paperButton(
                cx - btnW / 2, startY + (btnH + gap) * row++,
                btnW, btnH,
                Component.literal("⚙ НАСТРОЙКИ "),
                b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)),
                delay * 1, null, null));

        if (canPublish) {
            addRenderableWidget(PaperWidgets.paperButton(
                    cx - btnW / 2, startY + (btnH + gap) * row++,
                    btnW, btnH,
                    Component.literal("⇅ ОТКРЫТЬ МИР ДЛЯ СЕТИ "),
                    b -> this.minecraft.setScreen(new ShareToLanScreen(this)),
                    delay * 2, PaperRender.INK_RED_DIM, null));
        }

        addRenderableWidget(PaperWidgets.paperButton(
                cx - btnW / 2, startY + (btnH + gap) * row++,
                btnW, btnH,
                Component.literal(singleplayer ? "◀ ВЫЙТИ ИЗ МИРА " : "◀ ОТКЛЮЧИТЬСЯ ОТ СЕРВЕРА "),
                b -> disconnect(),
                delay * 3, PaperRender.INK_SOFT, null));
    }

    private void disconnect() {
        boolean singleplayer = this.minecraft.isLocalServer();
        if (this.minecraft.level != null) {
            this.minecraft.level.disconnect();
        }
        if (singleplayer) {
            this.minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel ")));
        } else {
            this.minecraft.clearLevel();
        }
        Screen title = new OtborTitleScreen();
        if (singleplayer) {
            this.minecraft.setScreen(title);
        } else {
            this.minecraft.setScreen(new JoinMultiplayerScreen(title));
        }
    }

    private void resumeGame() {
        this.minecraft.setScreen(null);
        this.minecraft.mouseHandler.grabMouse();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            resumeGame();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, this.width, this.height, 0xB8000000);
        for (int y = 0; y < this.height; y += 3) {
            gfx.fill(0, y, this.width, y + 1, 0x10000000);
        }

        long age = Math.max(0, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 450f));

        int cx = this.width / 2;
        String title = showMenu ? "АНТРАКТ " : "СОХРАНЕНИЕ... ";
        float baseScale = this.width < 500 ? 2.4f : 3.2f;
        float scale = baseScale * scaleMultiplier;
        int titleW = (int) (this.font.width(title) * scale);
        int titleY = Math.max((int)(30 * scaleMultiplier), this.height / 2 - (int)(100 * scaleMultiplier))
                + (int) ((1f - appear) * 14f * scaleMultiplier);

        gfx.pose().pushPose();
        gfx.pose().translate(cx - titleW / 2f, titleY, 0);
        gfx.pose().scale(scale, scale, 1f);

        float pulse = 0.5f + 0.5f * Mth.sin(PaperRender.gameTime() * 0.05f);
        int glitchAlpha = (int) (80 * pulse);
        gfx.drawString(this.font, title, 1, 0, (glitchAlpha << 24) | 0x8B0E0E, false);
        int mainAlpha = (int) (0xFF * appear);
        gfx.drawString(this.font, title, 0, 0, (mainAlpha << 24) | 0xFFFFFF, true);
        gfx.pose().popPose();

        int lineY = titleY + (int) (9 * scale) + (int)(6 * scaleMultiplier);
        int half = (int) (Math.min((int)(220 * scaleMultiplier), this.width / 2 - (int)(40 * scaleMultiplier)) * appear);
        int lineColor = PaperRender.withAlpha(PaperRender.INK_RED, 0.4f + 0.6f * pulse);
        gfx.fill(cx - half, lineY, cx + half, lineY + 1, lineColor);

        if (showMenu) {
            String sub = "Испытание приостановлено · ожидание кандидата ";
            gfx.drawString(this.font, sub, cx - this.font.width(sub) / 2, lineY + (int)(8 * scaleMultiplier),
                    PaperRender.withAlpha(0xEFE3C2, 0.6f * appear), false);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}