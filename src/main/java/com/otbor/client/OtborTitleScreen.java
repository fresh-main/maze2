package com.otbor.client;
import com.labyrinthmod.common.init.ModSounds;
import com.otbor.client.widgets.PaperRender;
import com.otbor.client.widgets.PaperWidgets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class OtborTitleScreen extends Screen {
    private static final long EXIT_TRANSITION_MS = 240L;
    private long exitStart = 0L;
    private Screen pendingScreen = null;
    private long enterStart = 0L;
    private float scaleMultiplier = 1.0f;
    private static final long COMING_SOON_MS = 1800L;
    private long comingSoonStart = 0L;
    private int noteX;
    private int noteY;
    private MenuMusicInstance menuMusic;

    public OtborTitleScreen() {
        super(Component.literal("OTBOR "));
    }

    private void requestNavigate(Screen target) {
        if (exitStart != 0L) return;
        this.pendingScreen = target;
        this.exitStart = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        startNextMenuTrack();
        this.enterStart = System.currentTimeMillis();
        scaleMultiplier = 1.0f;

        int cx = this.width / 2;

        int cardW = (int)(168 * scaleMultiplier);
        int cardH = (int)(220 * scaleMultiplier);
        int gap = (int)(20 * scaleMultiplier);
        int sq = (int)(56 * scaleMultiplier);
        int sqGap = (int)(12 * scaleMultiplier);
        int extra = sq + gap;

        int count = 4;
        int totalRowW = cardW * count + gap * (count - 1) + extra;
        int cardsY = Math.max(this.height - cardH - (int)(100 * scaleMultiplier), (int)(200 * scaleMultiplier));

        if (totalRowW > this.width - (int)(40 * scaleMultiplier)) {
            cardW = Math.max((int)(120 * scaleMultiplier), (this.width - (int)(40 * scaleMultiplier) - gap * (count - 1) - extra) / count);
            totalRowW = cardW * count + gap * (count - 1) + extra;
        }

        int startX = cx - totalRowW / 2;
        int step = cardW + gap;

        addRenderableWidget(PaperWidgets.noteCard(
                startX, cardsY, cardW, cardH,
                0,
                Component.literal("ВОЙТИ В"),
                "выбор сервера",
                "",
                "door",
                PaperWidgets.NoteCardVariant.BASE,
                false, -4f, 0L,
                b -> requestNavigate(new EnterMazeScreen(this))
        ));

        addRenderableWidget(PaperWidgets.noteCard(
                startX + step, cardsY, cardW, cardH,
                1,
                Component.literal("ИГРАТЬ"),
                "одиночное приключение",
                "выбор мира",
                "compass",
                PaperWidgets.NoteCardVariant.BASE,
                false, 0.5f, 45L,
                b -> requestNavigate(new OtborWorldSelectionScreen(this))
        ));

        int colX = startX + step * 2;
        int colY = cardsY + (cardH - (sq * 2 + sqGap)) / 2;
        noteX = colX + sq / 2;
        noteY = colY - (int)(16 * scaleMultiplier);

        addRenderableWidget(new IconButton(colX, colY, sq, true,
                Component.literal("Выход из игры"),
                () -> this.minecraft.stop()));

        addRenderableWidget(new IconButton(colX, colY + sq + sqGap, sq, false,
                Component.literal("Перевод"),
                () -> comingSoonStart = System.currentTimeMillis()));

        addRenderableWidget(PaperWidgets.noteCard(
                startX + step * 2 + extra, cardsY, cardW, cardH,
                2,
                Component.literal("УЗНАТЬ"),
                "кто создал этот ад?",
                "досье разработчиков",
                "eye",
                PaperWidgets.NoteCardVariant.LIGHT,
                true, 2.5f, 90L,
                b -> requestNavigate(new TheTruthScreen(this))
        ));

        addRenderableWidget(PaperWidgets.noteCard(
                startX + step * 3 + extra, cardsY, cardW, cardH,
                3,
                Component.literal("ИНСТРУКЦИЯ"),
                "О.Т.Б.О.Р",
                "настройки и управление",
                "gear",
                PaperWidgets.NoteCardVariant.DARK,
                false, -1.5f, 180L,
                b -> requestNavigate(new OtborInstructionScreen(this))
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (menuMusic == null || !Minecraft.getInstance().getSoundManager().isActive(menuMusic)) {
            startNextMenuTrack();
        }
    }

    @Override
    public void removed() {
        if (menuMusic != null) {
            menuMusic.stopMe();
            menuMusic = null;
        }
        super.removed();
    }

    private void startNextMenuTrack() {
        if (this.minecraft == null || menuMusic != null && this.minecraft.getSoundManager().isActive(menuMusic)) return;
        menuMusic = new MenuMusicInstance(ModSounds.MENU_EPIC.get());
        this.minecraft.getSoundManager().play(menuMusic);
    }

    private static class MenuMusicInstance extends AbstractSoundInstance {
        MenuMusicInstance(SoundEvent event) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.24f;
            this.pitch = 1.0f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        void stopMe() {
            Minecraft.getInstance().getSoundManager().stop(this);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        PaperRender.drawBoardBackground(gfx, this.width, this.height);

        renderHeader(gfx, partialTick);

        renderCornerScribbles(gfx);

        super.render(gfx, mouseX, mouseY, partialTick);

        renderCornerStamps(gfx);
        renderComingSoon(gfx);

        if (enterStart != 0L) {
            float p = Math.min(1f, (System.currentTimeMillis() - enterStart) / 220f);
            if (p >= 1f) {
                enterStart = 0L;
            } else {
                int alpha = (int) (255 * (1f - smoothstep(p)));
                gfx.fill(0, 0, this.width, this.height, (alpha << 24));
            }
        }

        if (exitStart != 0L) {
            long now = System.currentTimeMillis();
            float p = Math.min(1f, (now - exitStart) / (float) EXIT_TRANSITION_MS);
            int alpha = (int) (255 * smoothstep(p));
            gfx.fill(0, 0, this.width, this.height, (alpha << 24));
            if (p >= 1f && pendingScreen != null) {
                Screen next = pendingScreen;
                pendingScreen = null;
                exitStart = 0L;
                this.minecraft.setScreen(next);
            }
        }
    }

    private static float smoothstep(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (exitStart != 0L) return true;
        return super.mouseClicked(mx, my, button);
    }

    private void renderHeader(GuiGraphics gfx, float partialTick) {
        int cx = this.width / 2;
        Font font = this.font;

        int bannerW = Math.min((int)(520 * scaleMultiplier), this.width - (int)(80 * scaleMultiplier));
        int bannerH = (int)(110 * scaleMultiplier);
        int bannerX = cx - bannerW / 2;
        int bannerY = (int)(30 * scaleMultiplier);

        gfx.pose().pushPose();
        gfx.pose().translate(cx, bannerY + bannerH / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-0.8f));
        gfx.pose().translate(-bannerW / 2f, -bannerH / 2f, 0);

        PaperRender.drawPaperCard(gfx, 0, 0, bannerW, bannerH, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawTape(gfx, (int)(30 * scaleMultiplier), (int)(-8 * scaleMultiplier), (int)(60 * scaleMultiplier), (int)(14 * scaleMultiplier), 0xC0);
        PaperRender.drawTape(gfx, bannerW - (int)(90 * scaleMultiplier), (int)(-8 * scaleMultiplier), (int)(60 * scaleMultiplier), (int)(14 * scaleMultiplier), 0xC0);

        String kicker = "ДЕЛО №047 · СОВЕРШЕННО СЕКРЕТНО";
        int kickerW = font.width(kicker);
        gfx.drawString(font, kicker, bannerW / 2 - kickerW / 2, (int)(10 * scaleMultiplier),
                PaperRender.INK_FADED, false);

        long gt = PaperRender.gameTime();
        float pulse = 0.5f + 0.5f * Mth.sin((gt + partialTick) * 0.05f);

        String line1 = "БЕГУЩИЙ";
        float s1 = 3.0f * scaleMultiplier;
        int w1 = (int) (font.width(line1) * s1);
        gfx.pose().pushPose();
        gfx.pose().translate(bannerW / 2f - w1 / 2f, (int)(26 * scaleMultiplier), 0);
        gfx.pose().scale(s1, s1, 1f);
        int glitch = (int) (55 + 35 * pulse);
        gfx.drawString(font, line1, 1, 0, (glitch << 24) | 0x7A1F1F, false);
        gfx.drawString(font, line1, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        String line2 = "В ЛАБИРИНТЕ 2";
        float s2 = 2.4f * scaleMultiplier;
        int w2 = (int) (font.width(line2) * s2);
        gfx.pose().pushPose();
        gfx.pose().translate(bannerW / 2f - w2 / 2f, (int)(26 * scaleMultiplier) + (int)(9 * s1) + (int)(2 * scaleMultiplier), 0);
        gfx.pose().scale(s2, s2, 1f);
        gfx.drawString(font, line2, 0, 0, PaperRender.INK_RED, false);
        gfx.pose().popPose();

        String ver = "MINECRAFT - IVENT - v2.0";
        int verW = font.width(ver);
        gfx.drawString(font, ver, bannerW / 2 - verW / 2, bannerH - (int)(14 * scaleMultiplier),
                PaperRender.INK_FADED, false);

        gfx.pose().popPose();

        int stampCx = bannerX + bannerW - (int)(20 * scaleMultiplier);
        int stampCy = bannerY + bannerH - (int)(12 * scaleMultiplier);
        gfx.pose().pushPose();
        gfx.pose().translate(stampCx, stampCy, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(14f));
        PaperRender.drawRoundStamp(gfx, font, 0, 0, (int)(32 * scaleMultiplier), "О.Т.Б.О.Р ", "СЕКРЕТНО ",
                PaperRender.withAlpha(PaperRender.INK_RED, 0.8f));
        gfx.pose().popPose();
    }

    private void renderCornerScribbles(GuiGraphics gfx) {
        Font font = this.font;
        gfx.pose().pushPose();
        gfx.pose().translate(this.width - (int)(180 * scaleMultiplier), (int)(24 * scaleMultiplier), 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(8f));
        PaperRender.drawScribble(gfx, font, "\"помни - они смотрят\" ", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.8f));
        gfx.pose().popPose();

        gfx.pose().pushPose();
        gfx.pose().translate((int)(20 * scaleMultiplier), (int)(170 * scaleMultiplier), 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6f));
        PaperRender.drawScribble(gfx, font, "день 47. ", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.85f));
        PaperRender.drawScribble(gfx, font, "стены снова ", 0, (int)(12 * scaleMultiplier),
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.85f));
        PaperRender.drawScribble(gfx, font, "сдвинулись. ", 0, (int)(24 * scaleMultiplier),
                PaperRender.withAlpha(PaperRender.INK_SOFT, 0.85f));
        gfx.pose().popPose();
    }

    private void renderCornerStamps(GuiGraphics gfx) {
        Font font = this.font;
        long gt = PaperRender.gameTime();
        boolean visible = ((gt / 20L) % 5L) != 0L;
        if (visible) {
            gfx.pose().pushPose();
            gfx.pose().translate((int)(60 * scaleMultiplier), this.height - (int)(30 * scaleMultiplier), 0);
            gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-8f));
            PaperRender.drawRectStamp(gfx, font, "СЕКРЕТНО", 0, 0,
                    PaperRender.withAlpha(PaperRender.INK_RED, 0.85f));
            gfx.pose().popPose();
        }
    }

    private void renderComingSoon(GuiGraphics gfx) {
        if (comingSoonStart == 0L) return;
        if (System.currentTimeMillis() - comingSoonStart >= COMING_SOON_MS) {
            comingSoonStart = 0L;
            return;
        }
        String text = "СКОРО...";
        int w = this.font.width(text) + 16;
        int h = this.font.lineHeight + 10;
        gfx.pose().pushPose();
        gfx.pose().translate(noteX, noteY, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-4f));
        gfx.pose().translate(-w / 2f, -h / 2f, 0);
        PaperRender.drawPaperCard(gfx, 0, 0, w, h, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawScribble(gfx, this.font, text, 8, 5, PaperRender.withAlpha(PaperRender.INK_RED, 0.9f));
        gfx.pose().popPose();
    }


    @Override
    public boolean shouldCloseOnEsc() { return false; }

    private static class IconButton extends AbstractButton {
        private static final String[] POWER = {
                ".....##.....",
                ".....##.....",
                "..#..##..#..",
                ".##..##..##.",
                "##...##...##",
                "##........##",
                "##........##",
                "##........##",
                ".##......##.",
                "..##....##..",
                "...######..."
        };
        private static final String[] GLOBE = {
                "...######...",
                ".##.#..#.##.",
                ".#..#..#..#.",
                "#...#..#...#",
                "############",
                "#...#..#...#",
                "#...#..#...#",
                "############",
                ".#..#..#..#.",
                ".##.#..#.##.",
                "...######..."
        };

        private final boolean exit;
        private final Runnable action;

        IconButton(int x, int y, int size, boolean exit, Component narration, Runnable action) {
            super(x, y, size, size, narration);
            this.exit = exit;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            PaperRender.drawPaperCard(gfx, getX(), getY(), width, height, 1.0f, PaperRender.PAPER_LIGHT);
            boolean hot = isHoveredOrFocused();
            if (hot) {
                gfx.fill(getX(), getY(), getX() + width, getY() + height,
                        PaperRender.withAlpha(PaperRender.INK_RED, 0.12f));
            }
            int color = hot ? PaperRender.INK_RED : PaperRender.INK;
            String[] bmp = exit ? POWER : GLOBE;
            int unit = Math.max(1, width / 24);
            int ix = getX() + (width - bmp[0].length() * unit) / 2;
            int iy = getY() + (height - bmp.length * unit) / 2;
            for (int row = 0; row < bmp.length; row++) {
                for (int col = 0; col < bmp[row].length(); col++) {
                    if (bmp[row].charAt(col) == '#') {
                        int px = ix + col * unit;
                        int py = iy + row * unit;
                        gfx.fill(px, py, px + unit, py + unit, color);
                    }
                }
            }
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
