package com.otbor.client;
import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
public class OtborKeyBindsScreen extends Screen {
    private int ROW_H;
    private int PANEL_W;

    private final Screen parent;
    private final Options options;

    private String[] rowCategory;
    private KeyMapping[] rowBinding;
    private int rowCount;

    private KeyMapping editing = null;
    private int scroll = 0;
    private int listTop;
    private int listBottom;
    private float scaleMultiplier = 1.0f;

    public OtborKeyBindsScreen(Screen parent) {
        super(Component.literal("НАЗНАЧЕНИЕ КЛАВИШ "));
        this.parent = parent;
        this.options = Minecraft.getInstance().options;
    }

    @Override
    protected void init() {
        super.init();

        double guiScale = minecraft.getWindow().getGuiScale();
        if (guiScale <= 0.0) guiScale = 2.0;
        scaleMultiplier = (float) ((int) Math.round(guiScale)) / 2.0f;

        ROW_H = (int)(18 * scaleMultiplier);
        PANEL_W = (int)(540 * scaleMultiplier);

        KeyMapping[] sorted = Arrays.stream(options.keyMappings)
                .sorted(Comparator
                        .comparing((KeyMapping k) -> k.getCategory())
                        .thenComparing(k -> Component.translatable(k.getName()).getString()))
                .toArray(KeyMapping[]::new);

        List<String> cats = new ArrayList<>();
        List<KeyMapping> binds = new ArrayList<>();
        String last = null;
        for (KeyMapping km : sorted) {
            String cat = km.getCategory();
            if (!cat.equals(last)) {
                cats.add(cat);
                binds.add(null);
                last = cat;
            }
            cats.add(null);
            binds.add(km);
        }
        rowCount = cats.size();
        rowCategory = cats.toArray(new String[0]);
        rowBinding = binds.toArray(new KeyMapping[0]);

        listTop = (int)(78 * scaleMultiplier);
        listBottom = this.height - (int)(54 * scaleMultiplier);

        addRenderableWidget(PaperWidgets.paperButton(
                (int)(30 * scaleMultiplier), (int)(30 * scaleMultiplier),
                (int)(100 * scaleMultiplier), (int)(22 * scaleMultiplier),
                Component.literal(" <- НАЗАД "),
                b -> this.minecraft.setScreen(parent),
                0L, PaperRender.INK_SOFT, null));

        addRenderableWidget(PaperWidgets.paperButton(
                this.width / 2 - (int)(210 * scaleMultiplier), this.height - (int)(34 * scaleMultiplier),
                (int)(200 * scaleMultiplier), (int)(24 * scaleMultiplier),
                Component.literal("СБРОСИТЬ ВСЕ "),
                b -> { for (KeyMapping k : options.keyMappings) k.setKey(k.getDefaultKey()); KeyMapping.resetMapping(); },
                0L, PaperRender.INK_SOFT, null));

        addRenderableWidget(PaperWidgets.paperButton(
                this.width / 2 + (int)(10 * scaleMultiplier), this.height - (int)(34 * scaleMultiplier),
                (int)(200 * scaleMultiplier), (int)(24 * scaleMultiplier),
                Component.literal("ГОТОВО "),
                b -> this.minecraft.setScreen(parent),
                0L, PaperRender.INK_RED, null));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (editing != null) {
            if (button >= 0 && button <= 2) {
                options.setKey(editing, InputConstants.Type.MOUSE.getOrCreate(button));
                editing = null;
                KeyMapping.resetMapping();
                return true;
            }
        }

        if (editing == null && my >= listTop && my <= listBottom) {
            int panelX = this.width / 2 - PANEL_W / 2;
            int rowsShown = visibleRows();
            int btnX = panelX + PANEL_W - (int)(160 * scaleMultiplier);
            int btnW = (int)(90 * scaleMultiplier);
            int btnH = ROW_H - (int)(4 * scaleMultiplier);
            int resetX = btnX + btnW + (int)(6 * scaleMultiplier);
            int resetW = (int)(50 * scaleMultiplier);

            for (int i = 0; i < rowsShown; i++) {
                int idx = i + scroll;
                if (idx >= rowCount) break;
                if (rowBinding[idx] == null) continue;
                int rowY = listTop + i * ROW_H;
                if (my < rowY + (int)(2 * scaleMultiplier) || my > rowY + (int)(2 * scaleMultiplier) + btnH) continue;

                if (mx >= btnX && mx <= btnX + btnW) {
                    editing = rowBinding[idx];
                    return true;
                }
                if (mx >= resetX && mx <= resetX + resetW) {
                    KeyMapping b = rowBinding[idx];
                    b.setKey(b.getDefaultKey());
                    KeyMapping.resetMapping();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (editing != null) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                options.setKey(editing, InputConstants.UNKNOWN);
            } else {
                options.setKey(editing, InputConstants.getKey(keyCode, scanCode));
            }
            editing = null;
            KeyMapping.resetMapping();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, rowCount - visibleRows());
        scroll = (int) Math.max(0, Math.min(max, scroll - delta * 3));
        return true;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        Font font = this.font;
        String kicker = "ФАЙЛ №04 · СХЕМА УПРАВЛЕНИЯ ";
        int kw = font.width(kicker);
        gfx.drawString(font, kicker, this.width / 2 - kw / 2, (int)(34 * scaleMultiplier), 0xFFB8A581, false);

        String title = "НАЗНАЧЕНИЕ КЛАВИШ ";
        float ts = 2.0f * scaleMultiplier;
        int tw = (int) (font.width(title) * ts);
        gfx.pose().pushPose();
        gfx.pose().translate(this.width / 2f - tw / 2f, (int)(44 * scaleMultiplier), 0);
        gfx.pose().scale(ts, ts, 1f);
        gfx.drawString(font, title, 0, 0, PaperRender.PAPER_LIGHT, false);
        gfx.pose().popPose();

        int panelX = this.width / 2 - PANEL_W / 2;
        int panelY = listTop - (int)(8 * scaleMultiplier);
        int panelH = (listBottom - listTop) + (int)(16 * scaleMultiplier);
        PaperRender.drawPaper(gfx, panelX, panelY, PANEL_W, panelH, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, panelX + (int)(12 * scaleMultiplier), panelY + (int)(8 * scaleMultiplier), false);
        PaperRender.drawPin(gfx, panelX + PANEL_W - (int)(12 * scaleMultiplier), panelY + (int)(8 * scaleMultiplier), true);

        int rowsShown = visibleRows();
        int btnX = panelX + PANEL_W - (int)(160 * scaleMultiplier);
        int btnW = (int)(90 * scaleMultiplier);
        int btnH = ROW_H - (int)(4 * scaleMultiplier);
        int resetX = btnX + btnW + (int)(6 * scaleMultiplier);
        int resetW = (int)(50 * scaleMultiplier);

        for (int i = 0; i < rowsShown; i++) {
            int idx = i + scroll;
            if (idx >= rowCount) break;
            int rowY = listTop + i * ROW_H;

            if (rowCategory[idx] != null) {
                String catLoc = Component.translatable(rowCategory[idx]).getString();
                gfx.drawString(font, "-- " + catLoc + " --",
                        panelX + (int)(12 * scaleMultiplier), rowY + (int)(4 * scaleMultiplier), PaperRender.INK_RED, false);
                continue;
            }

            KeyMapping k = rowBinding[idx];
            if (k == null) continue;

            String name = Component.translatable(k.getName()).getString();
            gfx.drawString(font, name, panelX + (int)(20 * scaleMultiplier), rowY + (int)(5 * scaleMultiplier),
                    k == editing ? PaperRender.INK_RED : PaperRender.INK, false);

            boolean hover = mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= rowY + (int)(2 * scaleMultiplier) && mouseY <= rowY + (int)(2 * scaleMultiplier) + btnH;
            drawKeyButton(gfx, font, btnX, rowY + (int)(2 * scaleMultiplier), btnW, btnH,
                    k == editing ? " > нажми < " : k.getTranslatedKeyMessage().getString(),
                    k == editing, hover);

            boolean rHover = mouseX >= resetX && mouseX <= resetX + resetW
                    && mouseY >= rowY + (int)(2 * scaleMultiplier) && mouseY <= rowY + (int)(2 * scaleMultiplier) + btnH;
            drawKeyButton(gfx, font, resetX, rowY + (int)(2 * scaleMultiplier), resetW, btnH, "сброс ", false, rHover);
        }

        if (rowCount > visibleRows()) {
            int sbX = panelX + PANEL_W - (int)(4 * scaleMultiplier);
            int sbY = panelY + (int)(10 * scaleMultiplier);
            int sbH = panelH - (int)(20 * scaleMultiplier);
            gfx.fill(sbX, sbY, sbX + (int)(2 * scaleMultiplier), sbY + sbH,
                    PaperRender.withAlpha(PaperRender.INK_FADED, 0.4f));
            int thumbH = Math.max((int)(16 * scaleMultiplier), sbH * visibleRows() / rowCount);
            int maxScroll = Math.max(1, rowCount - visibleRows());
            int thumbY = sbY + (sbH - thumbH) * scroll / maxScroll;
            gfx.fill(sbX - (int)(1 * scaleMultiplier), thumbY, sbX + (int)(3 * scaleMultiplier), thumbY + thumbH, PaperRender.INK_RED);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawKeyButton(GuiGraphics gfx, Font font, int x, int y, int w, int h,
                               String label, boolean editing, boolean hover) {
        int bg = editing ? PaperRender.INK_RED : (hover ? PaperRender.PAPER_LIGHT : PaperRender.PAPER_BASE);
        int border = editing ? PaperRender.PAPER_LIGHT : PaperRender.INK;
        int textColor = editing ? PaperRender.PAPER_LIGHT : PaperRender.INK;

        gfx.fill(x, y, x + w, y + h, bg);
        gfx.fill(x, y, x + w, y + 1, border);
        gfx.fill(x, y + h - 1, x + w, y + h, border);
        gfx.fill(x, y, x + 1, y + h, border);
        gfx.fill(x + w - 1, y, x + w, y + h, border);

        int lw = font.width(label);
        gfx.drawString(font, label, x + w / 2 - lw / 2, y + (h - (int)(8 * scaleMultiplier)) / 2, textColor, false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}