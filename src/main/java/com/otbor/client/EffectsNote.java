package com.otbor.client;

import com.otbor.client.widgets.PaperRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.List;

/**
 * Paper-листок «АНОМАЛИИ · СТАТУС» — рисует список активных эффектов игрока
 * в стилистике мода. Выносится наружу слева от основного листа E-инвентаря.
 *
 * Геометрия рассчитана от left/top экрана инвентаря (см. InventoryScreenMixin):
 * листок располагается по координатам {@code (x - OFFSET_X, y + OFFSET_Y)}.
 */
public final class EffectsNote {

    public static final int WIDTH = 132;
    public static final int OFFSET_X = WIDTH + 8;
    public static final int OFFSET_Y = 22;

    private static final int PAD_X = 8;
    private static final int HEADER_H = 32;
    private static final int ROW_H = 26;
    private static final int FOOTER_H = 22;
    private static final int BADGE_SIZE = 18;

    private EffectsNote() {}

    public static void draw(GuiGraphics gfx, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;

        Font font = mc.font;
        Collection<MobEffectInstance> effects = p.getActiveEffects();

        int rows = Math.max(1, effects.size());
        int height = HEADER_H + rows * ROW_H + FOOTER_H;

        gfx.pose().pushPose();
        gfx.pose().translate(x + WIDTH / 2f, y + height / 2f, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-1.5f));
        gfx.pose().translate(-WIDTH / 2f, -height / 2f, 0);

        // EffectsNote рендерится КАЖДЫЙ кадр пока активны эффекты — drawPaperCard вместо drawPaper.
        PaperRender.drawPaperCard(gfx, 0, 0, WIDTH, height, 1.0f, PaperRender.PAPER_LIGHT);
        PaperRender.drawPin(gfx, 12, 6, true);
        PaperRender.drawPin(gfx, WIDTH - 12, 6, false);

        String kicker = "АНОМАЛИИ";
        gfx.drawString(font, kicker, WIDTH / 2 - font.width(kicker) / 2, 5,
                PaperRender.INK_FADED, false);

        String head = "СТАТУС";
        float headScale = 1.4f;
        int headW = (int) (font.width(head) * headScale);
        gfx.pose().pushPose();
        gfx.pose().translate(WIDTH / 2f - headW / 2f, 14, 0);
        gfx.pose().scale(headScale, headScale, 1f);
        gfx.drawString(font, head, 0, 0, PaperRender.INK, false);
        gfx.pose().popPose();

        PaperRender.drawHandDivider(gfx, PAD_X, HEADER_H - 3, WIDTH - PAD_X * 2,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.7f));

        if (effects.isEmpty()) {
            String none = "норма · чисто";
            gfx.drawString(font, none, WIDTH / 2 - font.width(none) / 2, HEADER_H + 6,
                    PaperRender.INK_FADED, false);
        } else {
            int row = 0;
            for (MobEffectInstance e : effects) {
                drawEffectRow(gfx, font, e,
                        PAD_X, HEADER_H + row * ROW_H,
                        WIDTH - PAD_X * 2);
                row++;
            }
        }

        // Печать строго внутри листка: ставим её по центру нижней «полосы» (FOOTER_H).
        int stampY = height - FOOTER_H / 2 - 2;
        gfx.pose().pushPose();
        gfx.pose().translate(WIDTH / 2f, stampY, 0);
        gfx.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-3f));
        PaperRender.drawRectStamp(gfx, font, "ОТДЕЛ ЗДОРОВЬЯ", 0, 0,
                PaperRender.withAlpha(PaperRender.INK_RED, 0.85f));
        gfx.pose().popPose();

        gfx.pose().popPose();
    }

    private static void drawEffectRow(GuiGraphics gfx, Font font, MobEffectInstance effInst,
                                       int x, int y, int rowW) {
        MobEffect eff = effInst.getEffect();

        int color = eff.getColor() | 0xFF000000;
        int by = y + (ROW_H - BADGE_SIZE) / 2;
        gfx.fill(x, by, x + BADGE_SIZE, y + ROW_H, 0x60000000);
        gfx.fill(x, by, x + BADGE_SIZE, by + 1, PaperRender.INK);
        gfx.fill(x, by + BADGE_SIZE - 1, x + BADGE_SIZE, by + BADGE_SIZE, PaperRender.INK);
        gfx.fill(x, by, x + 1, by + BADGE_SIZE, PaperRender.INK);
        gfx.fill(x + BADGE_SIZE - 1, by, x + BADGE_SIZE, by + BADGE_SIZE, PaperRender.INK);
        gfx.fill(x + 2, by + 2, x + BADGE_SIZE - 2, by + BADGE_SIZE - 2, color);

        int textX = x + BADGE_SIZE + 4;
        int textW = rowW - BADGE_SIZE - 4;

        // Имя эффекта: при необходимости добавляем римское усиление в строку,
        // ничего не накладывая поверх текста.
        Component nameComp = Component.translatable(eff.getDescriptionId());
        String full = nameComp.getString();
        if (effInst.getAmplifier() > 0) {
            full = full + " " + romanShort(effInst.getAmplifier() + 1);
        }
        int textColor = eff.isBeneficial() ? PaperRender.INK : PaperRender.INK_RED;
        List<FormattedCharSequence> wrapped = font.split(Component.literal(full), textW);
        FormattedCharSequence line = wrapped.isEmpty()
                ? Component.literal(full).getVisualOrderText()
                : wrapped.get(0);
        gfx.drawString(font, line, textX, y + 3, textColor, false);

        String dur = effInst.getDuration() < 0
                ? "∞"
                : MobEffectUtil.formatDuration(effInst, 1.0f).getString();
        gfx.drawString(font, dur, textX, y + 14,
                PaperRender.INK_FADED, false);
    }

    private static String romanShort(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
