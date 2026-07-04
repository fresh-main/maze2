package com.labyrinthmod.client.mixin;

import com.otbor.client.CustomShaderOptionList;
import com.otbor.client.CustomShaderPackList;
import com.mojang.blaze3d.platform.InputConstants;
import com.otbor.client.widgets.PaperButton;
import com.otbor.client.widgets.PaperRender;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(value = ShaderPackScreen.class, remap = false)
public abstract class MixinShaderPackScreenPaper extends Screen {
    @Shadow @Final private Screen parent;
    @Shadow @Final private MutableComponent irisTextComponent;
    @Shadow private ShaderPackSelectionList shaderPackList;
    @Shadow private @Nullable ShaderPackOptionList shaderOptionList;
    @Shadow private @Nullable NavigationController navigation;
    @Shadow private Button screenSwitchButton;
    @Shadow private Component notificationDialog;
    @Shadow private int notificationDialogTimer;
    @Shadow private @Nullable AbstractElementWidget<?> hoveredElement;
    @Shadow private Optional<Component> hoveredElementCommentTitle;
    @Shadow private List<FormattedCharSequence> hoveredElementCommentBody;
    @Shadow private int hoveredElementCommentTimer;
    @Shadow private boolean optionMenuOpen;
    @Shadow private boolean dropChanges;
    @Shadow private MutableComponent developmentComponent;
    @Shadow private MutableComponent updateComponent;
    @Shadow private boolean guiHidden;
    @Shadow private float guiButtonHoverTimer;
    @Shadow private Button openFolderButton;
    @Shadow @Final private static Component SELECT_TITLE;
    @Shadow @Final private static Component CONFIGURE_TITLE;
    @Shadow @Final public static Set<Runnable> TOP_LAYER_RENDER_QUEUE;

    @Shadow public abstract void applyChanges();
    @Shadow public abstract void refreshScreenSwitchButton();
    @Shadow public abstract boolean isDisplayingComment();
    @Shadow public abstract void dropChangesAndClose();

    protected MixinShaderPackScreenPaper(Component component) {
        super(component);
    }

    private int cardY;
    private int actualCardH;
    private static final int TABS_W = 150;
    private static final int SLIDER_W = 10;
    private static final int CARD_W = 440;
    private static final int CARD_H = 360;
    private static final int HEADER_H = 36;
    private static final int PADDING = 14;
    private static final int TAB_H = 42;
    private static final int TAB_GAP = 6;

    // =======================================================================
    // НАСТРОЙКА СДВИГА СПИСКА:
    // Измените это число, чтобы сдвинуть ТОЛЬКО список влево (в пикселях).
    // =======================================================================
    private static final int LIST_SHIFT_LEFT = 0;

    private int contentX, contentY, contentW, contentH;
    private CustomShaderPackList customShaderPackList;

    @Overwrite(remap = false)
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        PaperRender.drawBoardBackground(guiGraphics, this.width, this.height);

        if (Screen.hasControlDown() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), 68)) {
            Minecraft.getInstance().setScreen(new ConfirmScreen((option) -> {
                Iris.setDebug(option);
                Minecraft.getInstance().setScreen((Screen)(Object)this);
            }, Component.literal("Shader debug mode toggle"), Component.literal("Debug mode helps investigate problems and shows shader errors. Would you like to enable it?"), Component.literal("Yes"), Component.literal("No")));
        }

        int cx = this.width / 2;
        int totalW = TABS_W + SLIDER_W + CARD_W;
        int startX = cx - totalW / 2;
        int tabsX = startX;
        int cardX = startX + TABS_W + SLIDER_W;

        renderMainCard(guiGraphics, cardX, this.cardY, this.actualCardH);

        if (!this.guiHidden) {
            int listLeft = this.contentX - LIST_SHIFT_LEFT;
            int listRight = this.contentX + this.contentW - LIST_SHIFT_LEFT;

            guiGraphics.enableScissor(listLeft, this.contentY, listRight, this.contentY + this.contentH);
            if (this.optionMenuOpen && this.shaderOptionList != null) {
                this.shaderOptionList.render(guiGraphics, mouseX, mouseY, delta);
            } else if (this.customShaderPackList != null) {
                this.customShaderPackList.render(guiGraphics, mouseX, mouseY, delta);
            }
            guiGraphics.disableScissor();
        }

        renderTabsDecor(guiGraphics, tabsX, this.cardY);

        float previousHoverTimer = this.guiButtonHoverTimer;
        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, delta);
        }
        if (previousHoverTimer == this.guiButtonHoverTimer) {
            this.guiButtonHoverTimer = 0.0F;
        }

        if (!this.guiHidden) {
            drawPaperTitle(guiGraphics, this.title, (int)((double)this.width * 0.5F), 8);
            if (this.notificationDialog != null && this.notificationDialogTimer > 0) {
                drawPaperTitle(guiGraphics, this.notificationDialog, (int)((double)this.width * 0.5F), 21);
            } else if (this.optionMenuOpen) {
                drawPaperTitle(guiGraphics, CONFIGURE_TITLE, (int)((double)this.width * 0.5F), 21);
            } else {
                drawPaperTitle(guiGraphics, SELECT_TITLE, (int)((double)this.width * 0.5F), 21);
            }

            if (this.isDisplayingComment()) {
                int panelHeight = Math.max(50, 18 + this.hoveredElementCommentBody.size() * 10);
                int x = (int)(0.5F * (double)this.width) - 157;
                int y = this.height - (panelHeight + 4);
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(x + 157, y + panelHeight / 2f, 0);
                guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.3f));
                guiGraphics.pose().translate(-157, -panelHeight / 2f, 0);
                PaperRender.drawPaperCard(guiGraphics, 0, 0, 314, panelHeight, 1.0f, PaperRender.PAPER_LIGHT);
                PaperRender.drawPin(guiGraphics, 314 - 12, 8, true);
                guiGraphics.drawString(this.font, this.hoveredElementCommentTitle.orElse(Component.empty()), 4, 4, PaperRender.INK, false);
                for (int i = 0; i < this.hoveredElementCommentBody.size(); ++i) {
                    guiGraphics.drawString(this.font, this.hoveredElementCommentBody.get(i), 4, 16 + i * 10, PaperRender.INK_FADED, false);
                }
                guiGraphics.pose().popPose();
            }
        }

        for (Runnable render : TOP_LAYER_RENDER_QUEUE) {
            render.run();
        }
        TOP_LAYER_RENDER_QUEUE.clear();

        int bottomTextColor = PaperRender.INK_FADED;
        if (this.developmentComponent != null) {
            guiGraphics.drawString(this.font, this.developmentComponent, 2, this.height - 10, PaperRender.INK_RED);
            guiGraphics.drawString(this.font, this.irisTextComponent, 2, this.height - 20, bottomTextColor);
        } else if (this.updateComponent != null) {
            guiGraphics.drawString(this.font, this.updateComponent, 2, this.height - 10, PaperRender.INK_RED);
            guiGraphics.drawString(this.font, this.irisTextComponent, 2, this.height - 20, bottomTextColor);
        } else {
            guiGraphics.drawString(this.font, this.irisTextComponent, 2, this.height - 10, bottomTextColor);
        }
    }

    @Overwrite(remap = false)
    protected void init() {
        super.init();
        this.actualCardH = Math.min(CARD_H, (int)(this.height * 0.65));
        int totalHeight = this.actualCardH + 15 + 50;
        this.cardY = Math.max(55, (this.height - totalHeight) / 2 + 5);

        int cx = this.width / 2;
        int totalW = TABS_W + SLIDER_W + CARD_W;
        int startX = cx - totalW / 2;
        int cardX = startX + TABS_W + SLIDER_W;

        // Базовые координаты контента (для кнопок и фона карточки они НЕ меняются)
        this.contentX = cardX + PADDING;
        this.contentW = CARD_W - PADDING * 2;
        this.contentY = this.cardY + HEADER_H;
        this.contentH = this.actualCardH - HEADER_H - PADDING;

        // Сдвинутые координаты ТОЛЬКО для списка
        // Сдвинутые координаты ТОЛЬКО для списка
        int listLeft = this.contentX - LIST_SHIFT_LEFT;
        int listRight = this.contentX + this.contentW - LIST_SHIFT_LEFT;

        // ИСПРАВЛЕНИЕ: Считаем реальную ширину и высоту области списка
        int listWidth = listRight - listLeft;
        int listHeight = this.contentH;

        if (this.shaderPackList != null) {
            this.removeWidget(this.shaderPackList);
        }
        if (this.shaderOptionList != null) {
            this.removeWidget(this.shaderOptionList);
        }

        // ПЕРЕДАЕМ РЕАЛЬНЫЕ РАЗМЕРЫ (listWidth, listHeight), а не this.width/height!
        this.customShaderPackList = new CustomShaderPackList((ShaderPackScreen)(Object)this, this.minecraft,
                listWidth, listHeight, this.contentY, this.contentY + this.contentH,
                listLeft, listRight);

        this.shaderPackList = this.customShaderPackList;

        if (Iris.getCurrentPack().isPresent() && this.navigation != null) {
            ShaderPack currentPack = Iris.getCurrentPack().get();
            this.shaderOptionList = new CustomShaderOptionList((ShaderPackScreen)(Object)this, this.navigation, currentPack, this.minecraft,
                    listWidth, listHeight, this.contentY, this.contentY + this.contentH,
                    listLeft, listRight);
            this.navigation.setActiveOptionList(this.shaderOptionList);
            this.shaderOptionList.rebuild();
        } else {
            this.optionMenuOpen = false;
            this.shaderOptionList = null;
        }

        this.clearWidgets();
        if (!this.guiHidden) {
            if (this.optionMenuOpen && this.shaderOptionList != null) {
                this.addRenderableWidget(this.shaderOptionList);
            } else if (this.customShaderPackList != null) {
                this.addRenderableWidget(this.customShaderPackList);
            }

            int listBottom = this.contentY + this.contentH;
            int row2Y = listBottom + 7;
            int row1Y = row2Y + 24;

            // Кнопки центрируются относительно contentX (без сдвига)
            int contentCenterX = this.contentX + this.contentW / 2;

            int buttonWidth = 100;
            int gap = 8;
            int totalRow1Width = 3 * buttonWidth + 2 * gap;
            int leftX = contentCenterX - totalRow1Width / 2;
            int centerX = leftX + buttonWidth + gap;
            int rightX = centerX + buttonWidth + gap;

            this.addRenderableWidget(new PaperButton(rightX, row1Y, buttonWidth, 20, CommonComponents.GUI_DONE, (button) -> this.onClose()));
            this.addRenderableWidget(new PaperButton(centerX, row1Y, buttonWidth, 20, Component.translatable("options.iris.apply"), (button) -> this.applyChanges()));
            this.addRenderableWidget(new PaperButton(leftX, row1Y, buttonWidth, 20, CommonComponents.GUI_CANCEL, (button) -> this.dropChangesAndClose()));

            int folderButtonWidth = 152;
            int totalRow2Width = 2 * folderButtonWidth + gap;
            int switchX = contentCenterX - totalRow2Width / 2;
            int folderX = switchX + folderButtonWidth + gap;

            this.openFolderButton = new PaperButton(folderX, row2Y, folderButtonWidth, 20, Component.translatable("options.iris.openShaderPackFolder"), (button) -> {
                CompletableFuture.runAsync(() -> Util.getPlatform().openUri(Iris.getShaderpacksDirectoryManager().getDirectoryUri()));
            });
            this.addRenderableWidget(this.openFolderButton);

            this.screenSwitchButton = new PaperButton(switchX, row2Y, folderButtonWidth, 20, Component.translatable("options.iris.shaderPackList"), (button) -> {
                this.optionMenuOpen = !this.optionMenuOpen;
                this.applyChanges();
                this.setFocused(this.customShaderPackList != null ? this.customShaderPackList.getFocused() : null);
                this.init();
            });
            this.addRenderableWidget(this.screenSwitchButton);
            this.refreshScreenSwitchButton();
        }

        if (this.minecraft.level != null) {
            Component showOrHide = this.guiHidden ? Component.translatable("options.iris.gui.show") : Component.translatable("options.iris.gui.hide");
            this.addRenderableWidget(new PaperButton(this.width - 60, 10, 50, 20, showOrHide, (button) -> {
                this.guiHidden = !this.guiHidden;
                this.init();
            }));
        }

        this.hoveredElement = null;
        this.hoveredElementCommentTimer = 0;
    }

    private void drawPaperTitle(GuiGraphics gfx, Component text, int centerX, int y) {
        int textW = this.font.width(text);
        int padding = 6;
        int cardW = textW + padding * 2;
        int cardH = 14;
        gfx.pose().pushPose();
        gfx.pose().translate(centerX, y + cardH / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.3f));
        gfx.pose().translate(-cardW / 2f, -cardH / 2f, 0);
        PaperRender.drawPaperCard(gfx, 0, 0, cardW, cardH, 0.8f, PaperRender.PAPER_DARK);
        gfx.drawString(this.font, text, padding, (cardH - 8) / 2, PaperRender.INK, false);
        gfx.pose().popPose();
    }

    private void renderMainCard(GuiGraphics gfx, int x, int y, int h) {
        gfx.pose().pushPose();
        gfx.pose().translate(x + CARD_W / 2f, y + h / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.5f));
        gfx.pose().translate(-CARD_W / 2f, -h / 2f, 0);
        PaperRender.drawPaperCard(gfx, 0, 0, CARD_W, h, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawTape(gfx, (CARD_W / 2) - 35, -6, 70, 14, 0x90);
        PaperRender.drawPin(gfx, 16, 8, false);
        PaperRender.drawPin(gfx, CARD_W - 16, 8, true);
        String head = "§l      " + (this.optionMenuOpen ? 2 : 1) + " ·      " + (this.optionMenuOpen ? "НАСТРОЙКИ" : "ВЫБОР ПАКА");
        gfx.drawString(this.font, head, 16, 8, PaperRender.INK_FADED, false);
        gfx.pose().popPose();
    }

    private void renderTabsDecor(GuiGraphics gfx, int x, int y) {
        String[] labels = {"ВЫБОР ПАКА", "НАСТРОЙКИ"};
        for (int i = 0; i < 2; i++) {
            int ty = y + i * (TAB_H + TAB_GAP);
            boolean selected = (i == 0 && !this.optionMenuOpen) || (i == 1 && this.optionMenuOpen);
            gfx.pose().pushPose();
            gfx.pose().translate(x + TABS_W / 2f, ty + TAB_H / 2f, 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.3f));
            gfx.pose().translate(-TABS_W / 2f, -TAB_H / 2f, 0);
            int paper = selected ? PaperRender.PAPER_DARK : PaperRender.PAPER_LIGHT;
            PaperRender.drawPaperCard(gfx, 0, 0, TABS_W, TAB_H, 1.0f, paper);
            if (selected) {
                gfx.fill(0, 0, 4, TAB_H, PaperRender.INK_RED);
                PaperRender.drawPin(gfx, TABS_W - 12, 8, true);
            }
            gfx.drawString(this.font, labels[i], 10, 14, selected ? PaperRender.INK : PaperRender.INK_FADED, false);
            gfx.pose().popPose();
        }
    }
}