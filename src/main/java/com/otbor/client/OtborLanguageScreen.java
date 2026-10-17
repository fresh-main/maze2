package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class OtborLanguageScreen extends Screen {
    private final Screen parent;
    private boolean russianSelected;
    private boolean englishSelected;
    private long openedAt;

    public OtborLanguageScreen(Screen parent) {
        super(Component.translatable("otbor.language.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        openedAt = System.currentTimeMillis();

        String language = Minecraft.getInstance().options.languageCode;
        russianSelected = "ru_ru".equalsIgnoreCase(language);
        englishSelected = "en_us".equalsIgnoreCase(language);

        int panelW = Math.min(620, this.width - 40);
        int panelH = Math.min(360, this.height - 40);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        boolean sideBySide = panelW >= 520;

        if (sideBySide) {
            int gap = 14;
            int cardW = (panelW - 58 - gap) / 2;
            int cardY = panelY + 98;
            int cardH = 150;
            addRenderableWidget(new LanguageCardButton(panelX + 22, cardY, cardW, cardH,
                    "RU", "otbor.language.russian_name", "otbor.language.russian_detail", russianSelected,
                    () -> selectLanguage("ru_ru")));
            addRenderableWidget(new LanguageCardButton(panelX + 22 + cardW + gap, cardY, cardW, cardH,
                    "EN", "otbor.language.english_name", "otbor.language.english_detail", englishSelected,
                    () -> selectLanguage("en_us")));
        } else {
            int cardW = panelW - 44;
            int cardH = 72;
            int cardX = panelX + 22;
            int cardY = panelY + 91;
            addRenderableWidget(new LanguageCardButton(cardX, cardY, cardW, cardH,
                    "RU", "otbor.language.russian_name", "otbor.language.russian_detail", russianSelected,
                    () -> selectLanguage("ru_ru")));
            addRenderableWidget(new LanguageCardButton(cardX, cardY + cardH + 10, cardW, cardH,
                    "EN", "otbor.language.english_name", "otbor.language.english_detail", englishSelected,
                    () -> selectLanguage("en_us")));
        }

        int backW = Math.min(190, panelW - 60);
        addRenderableWidget(PaperWidgets.paperButton(
                this.width / 2 - backW / 2, panelY + panelH - 39, backW, 24,
                Component.translatable("gui.back"), b -> this.minecraft.setScreen(parent),
                0L, PaperRender.INK_FADED, null));
    }

    private void selectLanguage(String languageCode) {
        Minecraft mc = Minecraft.getInstance();
        if (languageCode.equalsIgnoreCase(mc.options.languageCode)) {
            mc.setScreen(parent);
            return;
        }

        mc.options.languageCode = languageCode;
        mc.options.save();
        mc.getLanguageManager().setSelected(languageCode);
        mc.reloadResourcePacks().thenRun(() -> mc.execute(() -> mc.setScreen(new OtborTitleScreen())));
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        long age = Math.max(0L, System.currentTimeMillis() - openedAt);
        float appear = PaperRender.easeOut(Math.min(1f, age / 260f));
        int panelW = Math.min(620, this.width - 40);
        int panelH = Math.min(360, this.height - 40);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        int slide = (int) ((1f - appear) * 12f);

        gfx.pose().pushPose();
        gfx.pose().translate(panelX + panelW / 2f, panelY + panelH / 2f + slide, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.45f));
        gfx.pose().translate(-panelW / 2f, -panelH / 2f, 0);

        // Фоновый лист, подложка и «следы» архивного дела.
        PaperRender.drawPaper(gfx, 3, 4, panelW, panelH, appear * 0.28f, 0xA0161210);
        PaperRender.drawPaper(gfx, 0, 0, panelW, panelH, appear, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 15, 9, false);
        PaperRender.drawPin(gfx, panelW - 15, 9, true);
        PaperRender.drawTape(gfx, panelW / 2 - 36, -7, 72, 14, 0xB0);

        int ink = PaperRender.INK_DARK;
        String kicker = Component.translatable("otbor.language.kicker").getString();
        gfx.drawString(this.font, kicker, 24, 15, PaperRender.INK_FADED, false);
        String page = Component.translatable("otbor.language.page").getString();
        gfx.drawString(this.font, page, panelW - this.font.width(page) - 24, 15, PaperRender.INK_FADED, false);

        String heading = Component.translatable("otbor.language.heading").getString();
        gfx.drawString(this.font, heading, 24, 35, ink, false);
        String description = Component.translatable("otbor.language.description").getString();
        gfx.drawString(this.font, description, 24, 52, PaperRender.INK_SOFT, false);
        PaperRender.drawHandDivider(gfx, 22, 76, panelW - 44,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.68f));

        // Нижняя архивная пометка связывает экран с остальными меню О.Т.Б.О.Р.
        int footerY = panelH - 58;
        PaperRender.drawInkStroke(gfx, 24, footerY, panelW - 48, 1,
                PaperRender.withAlpha(PaperRender.INK_FADED, 0.45f), 8L);
        String footer = Component.translatable("otbor.language.footer").getString();
        gfx.drawString(this.font, footer, 24, footerY + 11, PaperRender.INK_FADED, false);
        String fileCode = "OTB · 08 / 02";
        gfx.drawString(this.font, fileCode, panelW - this.font.width(fileCode) - 24, footerY + 11,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.8f), false);

        gfx.pose().popPose();

        super.render(gfx, mouseX, mouseY, partialTick);
        if (appear < 1f) {
            gfx.fill(0, 0, this.width, this.height, ((int) ((1f - appear) * 170f) << 24));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class LanguageCardButton extends AbstractButton {
        private final String code;
        private final String nameKey;
        private final String detailKey;
        private final boolean selected;
        private final Runnable action;

        private LanguageCardButton(int x, int y, int width, int height, String code,
                                   String nameKey, String detailKey, boolean selected, Runnable action) {
            super(x, y, width, height, Component.translatable(selected
                    ? "otbor.language.current"
                    : "otbor.language.choose"));
            this.code = code;
            this.nameKey = nameKey;
            this.detailKey = detailKey;
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean hot = isHoveredOrFocused();
            int paper = selected ? 0xFFF3E4C1 : PaperRender.PAPER_LIGHT;
            PaperRender.drawPaperCard(gfx, x, y, width, height, 1f, paper);
            if (selected || hot) {
                int accent = selected ? PaperRender.INK_RED : PaperRender.INK_SOFT;
                gfx.fill(x, y, x + 3, y + height, accent);
                if (hot) gfx.fill(x + 3, y + 2, x + width - 2, y + height - 2,
                        PaperRender.withAlpha(accent, selected ? 0.07f : 0.10f));
            }

            PaperRender.drawPin(gfx, x + 12, y + 8, selected);
            int badgeX = x + 15;
            int badgeY = y + (height - 46) / 2;
            gfx.fill(badgeX, badgeY, badgeX + 46, badgeY + 46, 0xFFE1D0AA);
            gfx.renderOutline(badgeX, badgeY, 46, 46, PaperRender.withAlpha(PaperRender.INK_RED, 0.72f));
            gfx.drawCenteredString(font, code, badgeX + 23, badgeY + 18,
                    selected || hot ? PaperRender.INK_RED : PaperRender.INK_DARK);

            int textX = x + 72;
            int availableTextWidth = width - (textX - x) - 13;
            if (height >= 120) {
                gfx.drawString(font, Component.translatable(nameKey), textX, y + 27,
                        PaperRender.INK_DARK, false);
                gfx.drawString(font, Component.translatable(detailKey), textX, y + 44,
                        PaperRender.INK_FADED, false);
                PaperRender.drawHandDivider(gfx, textX, y + 59, availableTextWidth,
                        PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));
                gfx.drawString(font, Component.translatable(selected ? "otbor.language.current" : "otbor.language.choose"),
                        textX, y + height - 22, selected ? PaperRender.INK_RED : PaperRender.INK_SOFT, false);
            } else {
                gfx.drawString(font, Component.translatable(nameKey), textX, y + 16,
                        PaperRender.INK_DARK, false);
                gfx.drawString(font, Component.translatable(detailKey), textX, y + 33,
                        PaperRender.INK_FADED, false);
                PaperRender.drawHandDivider(gfx, textX, y + 47, availableTextWidth,
                        PaperRender.withAlpha(PaperRender.INK_SOFT, 0.5f));
                gfx.drawString(font, Component.translatable(selected ? "otbor.language.current" : "otbor.language.choose"),
                        textX, y + 53, selected ? PaperRender.INK_RED : PaperRender.INK_SOFT, false);
            }
            if (selected) {
                gfx.drawCenteredString(font, "✓", x + width - 17, y + 9, PaperRender.INK_RED);
            } else {
                gfx.drawString(font, "→", x + width - 20, y + height - 22, PaperRender.INK_FADED, false);
            }
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
