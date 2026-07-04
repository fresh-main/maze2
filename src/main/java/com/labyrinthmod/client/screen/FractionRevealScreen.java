package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.init.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Атмосферный экран получения роли:
 *   • плавное затемнение фона до полной черноты
 *   • тонкая красная виньетка по краям
 *   • большое название роли с раскрытием по буквам
 *   • описание ниже (тот же эффект)
 *   • штамп-печать сбоку
 *   • после ~6 секунд экран сам закрывается, либо по клику/нажатию любой клавиши
 */
public class FractionRevealScreen extends Screen {

    private final FractionType fraction;
    private final FractionType maskFraction; // если предатель — отображаем как маску
    private long startMillis;

    // Тайминги синхронизированы со звуком pechat.ogg (длительность ~4.27с).
    // Звук стартует на 1.0с и идёт до ~5.27с экрана; печать букв растянута на этот же
    // интервал, чтобы анимация заканчивалась одновременно со звуком, а не раньше.
    private static final long FADE_IN_MS    = 1000;
    private static final long ROLE_DELAY_MS = 0;
    private static final long ROLE_REVEAL_MS= 4300;
    private static final long DESC_DELAY_MS = 2000;
    private static final long DESC_REVEAL_MS= 3300;
    private static final long FADE_OUT_MS   = 1500;
    private static final long HOLD_MS       = 8500;
    // Звук печати — единый для всей анимации, проигрывается один раз в момент,
    // когда начинают появляться буквы названия роли. Без повторов и наслоений.
    private boolean typeSoundPlayed = false;

    public FractionRevealScreen(int fractionId, String maskFractionName) {
        super(Component.empty());
        FractionType f = FractionType.fromId(fractionId);
        this.fraction = f != null ? f : FractionType.NONE;
        FractionType mask = null;
        if (this.fraction == FractionType.IMPOSTER && maskFractionName != null && !maskFractionName.isEmpty()) {
            mask = FractionType.fromName(maskFractionName);
        }
        this.maskFraction = mask;
    }

    @Override
    protected void init() {
        super.init();
        this.startMillis = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    /** Анимация неинтерруптируема — глотаем все клавиши и клики. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return true; }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) { return true; }

    @Override
    public boolean charTyped(char c, int modifiers) { return true; }

    @Override
    public boolean mouseClicked(double x, double y, int btn) { return true; }

    @Override
    public boolean mouseReleased(double x, double y, int btn) { return true; }

    @Override
    public boolean mouseScrolled(double x, double y, double d) { return true; }

    @Override
    public boolean mouseDragged(double x, double y, int btn, double dx, double dy) { return true; }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // НЕ вызываем super.render() — мы рисуем всё сами поверх затемнённого мира
        long elapsed = System.currentTimeMillis() - startMillis;
        if (elapsed > HOLD_MS + FADE_OUT_MS) {
            this.minecraft.setScreen(null);
            return;
        }

        int w = this.width;
        int h = this.height;

        // Плавное затухание всего экрана в конце: первые HOLD_MS — полная видимость,
        // далее линейно к нулю за FADE_OUT_MS.
        float fadeOut = elapsed <= HOLD_MS ? 1f
                : clamp01(1f - (elapsed - HOLD_MS) / (float) FADE_OUT_MS);
        float fadeOutSmooth = smoothStep(fadeOut);

        // 1) Плавное затемнение мира (с учётом затухания в конце)
        float fadeIn = clamp01(elapsed / (float) FADE_IN_MS);
        int darkAlpha = (int) (220 * smoothStep(fadeIn) * fadeOutSmooth);
        gfx.fill(0, 0, w, h, (darkAlpha << 24));

        // 2) Тонкая красная виньетка по краям (нарастает, потом затухает)
        renderVignette(gfx, w, h, fadeIn * fadeOutSmooth);

        // 3) Кикер-надпись «РОЛЬ ВЫДАНА» (мелкая, поверх затемнения)
        if (fadeIn >= 0.6f) {
            String kicker = "РОЛЬ · ВЫДАНА";
            float kAlpha = clamp01((fadeIn - 0.6f) / 0.4f) * fadeOutSmooth;
            int kColor = colorWithAlpha(0xCCCCCC, kAlpha);
            int kx = w / 2 - this.font.width(kicker) / 2;
            this.font.drawInBatch(kicker, kx, h / 2 - 80, kColor, false,
                    gfx.pose().last().pose(), gfx.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            gfx.flush();
        }

        // 4) Название роли — анимация раскрытия по буквам
        FractionType visibleFraction = (maskFraction != null) ? maskFraction : fraction;
        String name = visibleFraction.displayName.toUpperCase();
        long roleStart = FADE_IN_MS + ROLE_DELAY_MS;
        if (elapsed >= roleStart) {
            // Один раз на всю анимацию — pechat.ogg как сплошной фон-звук печати,
            // а не отдельный сэмпл на каждую букву.
            if (!typeSoundPlayed) {
                playTypeSound(1.0f, 1.0f);
                typeSoundPlayed = true;
            }
            float roleProgress = clamp01((elapsed - roleStart) / (float) ROLE_REVEAL_MS);
            renderRevealedTitle(gfx, name, w / 2, h / 2 - 50, visibleFraction.color, roleProgress, fadeOutSmooth);
        }

        // 5) Описание роли
        String desc = getDescription(visibleFraction);
        long descStart = DESC_DELAY_MS;
        if (elapsed >= descStart && !desc.isEmpty()) {
            float descProgress = clamp01((elapsed - descStart) / (float) DESC_REVEAL_MS);
            renderRevealedSubtitle(gfx, desc, w / 2, h / 2 + 8, descProgress, fadeOutSmooth);
        }

        // 6) Штамп
        if (elapsed >= DESC_DELAY_MS + 500) {
            float stampAlpha = clamp01((elapsed - DESC_DELAY_MS - 500) / 600f) * fadeOutSmooth;
            renderStamp(gfx, w / 2, h / 2 + 40, stampAlpha);
        }

    }

    private void playTypeSound(float volume, float pitch) {
        var sound = ModSounds.FRACTION_PECHAT.get();
        if (sound == null) return;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void renderVignette(GuiGraphics gfx, int w, int h, float intensity) {
        int vignetteRgb = (fraction == FractionType.IMPOSTER || fraction == FractionType.OPERATOR)
                ? 0x6B0F0F : 0x1A0E08;
        int maxThickness = Math.max(40, w / 8);
        for (int i = 0; i < maxThickness; i++) {
            float a = (1f - i / (float) maxThickness) * 0.35f * intensity;
            int c = colorWithAlpha(vignetteRgb, a);
            gfx.fill(0, i, w, i + 1, c);
            gfx.fill(0, h - i - 1, w, h - i, c);
            if (i < w) {
                gfx.fill(i, 0, i + 1, h, c);
                gfx.fill(w - i - 1, 0, w - i, h, c);
            }
        }
    }

    private void renderRevealedTitle(GuiGraphics gfx, String text, int cx, int cy, int color, float progress, float fadeMul) {
        if (text.isEmpty()) return;
        int totalChars = text.length();
        int visible = (int) Math.ceil(progress * totalChars);
        if (visible <= 0) return;
        String shown = text.substring(0, Math.min(visible, totalChars));

        float scale = 4.0f;
        gfx.pose().pushPose();
        int textW = (int) (this.font.width(shown) * scale);
        int x = cx - textW / 2;
        gfx.pose().translate(x, cy, 0);
        gfx.pose().scale(scale, scale, 1f);

        int shadowAlpha = (int) (0xCC * fadeMul) & 0xFF;
        int mainAlpha = (int) (0xFF * fadeMul) & 0xFF;
        // Тень
        this.font.drawInBatch(shown, 1, 1, (shadowAlpha << 24), false,
                gfx.pose().last().pose(), gfx.bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        // Основной цвет
        this.font.drawInBatch(shown, 0, 0, (mainAlpha << 24) | (color & 0xFFFFFF), false,
                gfx.pose().last().pose(), gfx.bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        gfx.flush();

        // Курсор-черта в конце (мигает) пока идёт анимация
        if (visible < totalChars && (System.currentTimeMillis() / 250) % 2 == 0) {
            int caretX = this.font.width(shown);
            gfx.fill(caretX + 2, 0, caretX + 4, this.font.lineHeight,
                    (mainAlpha << 24) | (color & 0xFFFFFF));
        }
        gfx.pose().popPose();
    }

    private void renderRevealedSubtitle(GuiGraphics gfx, String text, int cx, int cy, float progress, float fadeMul) {
        int totalChars = text.length();
        int visible = (int) Math.ceil(progress * totalChars);
        if (visible <= 0) return;
        String shown = text.substring(0, Math.min(visible, totalChars));

        float scale = 1.6f;
        gfx.pose().pushPose();
        int textW = (int) (this.font.width(shown) * scale);
        int x = cx - textW / 2;
        gfx.pose().translate(x, cy, 0);
        gfx.pose().scale(scale, scale, 1f);
        int shadowAlpha = (int) (0xCC * fadeMul) & 0xFF;
        int mainAlpha = (int) (0xFF * fadeMul) & 0xFF;
        this.font.drawInBatch(shown, 1, 1, (shadowAlpha << 24), false,
                gfx.pose().last().pose(), gfx.bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        this.font.drawInBatch(shown, 0, 0, (mainAlpha << 24) | 0xD9D9D9, false,
                gfx.pose().last().pose(), gfx.bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        gfx.flush();
        gfx.pose().popPose();
    }

    private void renderStamp(GuiGraphics gfx, int cx, int cy, float alpha) {
        String stampText = (fraction == FractionType.IMPOSTER) ? "СЕКРЕТНО" : "ПОДТВЕРЖДЕНО";
        int color = (fraction == FractionType.IMPOSTER) ? 0x9C1B1B : 0xA85A1F;
        int colorAlpha = colorWithAlpha(color, alpha);

        int w = this.font.width(stampText) + 14;
        int h = 16;
        int x = cx - w / 2;
        int y = cy - h / 2;

        gfx.pose().pushPose();
        gfx.pose().translate(cx, cy, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-7f));
        gfx.pose().translate(-cx, -cy, 0);

        // двойная рамка
        gfx.fill(x, y, x + w, y + 1, colorAlpha);
        gfx.fill(x, y + h - 1, x + w, y + h, colorAlpha);
        gfx.fill(x, y, x + 1, y + h, colorAlpha);
        gfx.fill(x + w - 1, y, x + w, y + h, colorAlpha);
        gfx.fill(x + 2, y + 2, x + w - 2, y + 3, colorAlpha);
        gfx.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, colorAlpha);
        gfx.fill(x + 2, y + 2, x + 3, y + h - 2, colorAlpha);
        gfx.fill(x + w - 3, y + 2, x + w - 2, y + h - 2, colorAlpha);

        gfx.drawString(this.font, stampText,
                x + w / 2 - this.font.width(stampText) / 2, y + 5, colorAlpha, false);
        gfx.pose().popPose();
    }

    private static String getDescription(FractionType f) {
        return com.labyrinthmod.common.event.FractionEvents.getFractionDescription(f);
    }

    private static int colorWithAlpha(int rgb, float a) {
        int alpha = (int) (Math.max(0f, Math.min(1f, a)) * 255);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static float smoothStep(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }
}
