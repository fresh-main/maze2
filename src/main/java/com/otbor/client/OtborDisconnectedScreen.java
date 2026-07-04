package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Замена ванильному {@link net.minecraft.client.gui.screens.DisconnectedScreen}:
 * связь с сервером оборвана / клиент кикнут — показываем бумажный «протокол изъятия»
 * с причиной и единственной paper-кнопкой возврата.
 *
 * Создаётся из {@link ClientEvents#onScreenOpen(net.minecraftforge.client.event.ScreenEvent.Opening)}
 * при перехвате DisconnectedScreen — туда уезжают title/reason/parent через reflection.
 */
public class OtborDisconnectedScreen extends Screen {

    private final Screen parent;
    private final Component reason;
    private final Component buttonText;

    private MultiLineLabel reasonLabel = MultiLineLabel.EMPTY;
    private long openedAt = -1L;

    public OtborDisconnectedScreen(Screen parent, Component title, Component reason, Component buttonText) {
        super(title);
        // Игнорируем переданный parent (часто это JoinMultiplayerScreen после
        // выхода/кика с сервера) — пользователь хочет всегда возвращаться в
        // главное меню, а не в список серверов.
        this.parent = new OtborTitleScreen();
        this.reason = reason != null ? reason : Component.literal("");
        this.buttonText = buttonText != null ? buttonText : CommonComponents.GUI_BACK;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();

        int paperW = paperWidth();
        int paperX = (this.width - paperW) / 2;
        int paperH = paperHeight();
        int paperY = (this.height - paperH) / 2;

        // Готовим многострочный текст причины — длинные kick-сообщения часто
        // содержат переносы и должны умещаться в paper-карточку.
        int maxTextW = paperW - 40;
        this.reasonLabel = MultiLineLabel.create(this.font, this.reason, maxTextW);

        int btnW = Math.min(280, paperW - 60);
        int btnH = 26;
        int btnX = this.width / 2 - btnW / 2;
        int btnY = paperY + paperH - btnH - 18;

        addRenderableWidget(PaperWidgets.paperButton(
                btnX, btnY, btnW, btnH,
                this.buttonText,
                b -> this.minecraft.setScreen(this.parent),
                0L, PaperRender.INK_RED, null));
    }

    private int paperWidth()  { return Math.min(this.width  - 60, 460); }
    private int paperHeight() { return Math.min(this.height - 60, 260); }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // Тёмный фон-«доска» как у инструкции/титульника.
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        long age = Math.max(0L, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 380f));
        float pulse = 0.5f + 0.5f * Mth.sin(PaperRender.gameTime() * 0.07f);

        int paperW = paperWidth();
        int paperH = paperHeight();
        int paperX = (this.width - paperW) / 2;
        int paperY = (this.height - paperH) / 2;
        int slide = (int) ((1f - appear) * 14f);

        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f, paperY + paperH / 2f + slide, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.6f));
        gfx.pose().translate(-paperW / 2f, -paperH / 2f, 0);

        PaperRender.drawPaper(gfx, 0, 0, paperW, paperH, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 16, 8, false);
        PaperRender.drawPin(gfx, paperW - 16, 8, true);

        Font font = this.font;

        // Шапка-кикер (мелкий слегка выгоревший текст).
        String kicker = "ПРОТОКОЛ ИЗЪЯТИЯ · ВНЕШТАТНАЯ СИТУАЦИЯ";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, paperW / 2 - kw / 2, 8,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.9f), false);

        // Большой заголовок (то, что пришло из ванильного DisconnectedScreen).
        String title = this.title.getString().toUpperCase(java.util.Locale.ROOT);
        if (title.isEmpty()) title = "СВЯЗЬ ОБОРВАНА";
        float titleScale = 2.1f;
        int titleW = (int) (font.width(title) * titleScale);
        int titleY = 24;
        gfx.pose().pushPose();
        gfx.pose().translate(paperW / 2f - titleW / 2f, titleY, 0);
        gfx.pose().scale(titleScale, titleScale, 1f);
        int glitch = (int) (60 + 50 * pulse);
        gfx.drawString(font, title, 1, 0, (glitch << 24) | 0x7A1F1F, false);
        gfx.drawString(font, title, -1, 0, (glitch << 24) | 0x224422, false);
        PaperRender.drawInkText(gfx, font, title, 0, 0, PaperRender.INK_DARK);
        gfx.pose().popPose();

        // Чернильная разделительная черта под заголовком, мигает в такт пульсу.
        int lineY = titleY + (int) (10 * titleScale) + 6;
        int lineW = paperW - 80;
        int lineColor = PaperRender.withAlpha(PaperRender.INK_RED, 0.55f + 0.45f * pulse);
        PaperRender.drawInkStroke(gfx, paperW / 2 - lineW / 2, lineY, lineW, 1, lineColor, 13L);

        // Малая подпись «причина».
        String causeLabel = "ПРИЧИНА";
        gfx.drawString(font, causeLabel, paperW / 2 - font.width(causeLabel) / 2, lineY + 8,
                PaperRender.INK_FADED, false);

        // Текст причины (ванильное reason — может быть многострочным).
        int reasonY = lineY + 22;
        if (this.reasonLabel != MultiLineLabel.EMPTY) {
            this.reasonLabel.renderCentered(gfx, paperW / 2, reasonY, 11, PaperRender.INK);
        }

        // Штамп-печать «СВЯЗЬ ОБОРВАНА» в правом нижнем углу листа.
        gfx.pose().pushPose();
        gfx.pose().translate(paperW - 60, paperH - 38, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-9f));
        PaperRender.drawRectStamp(gfx, font, "СВЯЗЬ ОБОРВАНА", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.85f));
        gfx.pose().popPose();

        gfx.pose().popPose();

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    /** Esc / закрытие — возвращаемся к родителю (обычно к титульнику или к списку серверов). */
    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent != null ? this.parent : new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
