package com.labyrinthmod.client;

import com.labyrinthmod.LabyrinthMod;
import com.labyrinthmod.common.capability.FractionProvider;
import com.labyrinthmod.common.capability.FractionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Записка-памятка справа от инвентаря: показывает текущую фракцию и краткое описание.
 * Рендерится через ContainerScreenEvent.Render.Background — pose не смещён, абсолютные координаты.
 */
@Mod.EventBusSubscriber(modid = LabyrinthMod.MOD_ID, value = Dist.CLIENT)
public class InventoryFractionNote {

    private static final int NOTE_W = 140;
    private static final int NOTE_H = 116;
    private static final int GAP = 12;
    private static final int PAD = 9;

    private InventoryFractionNote() {}

    @SubscribeEvent
    public static void onInventoryBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!(screen instanceof InventoryScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        FractionType fraction = player.getCapability(FractionProvider.FRACTION)
                .map(d -> d.hasFraction() ? d.getFraction() : FractionType.NONE)
                .orElse(FractionType.NONE);

        String mask = player.getCapability(FractionProvider.FRACTION)
                .map(d -> d.hasImposterMask() ? d.getImposterMask() : "")
                .orElse("");

        // Предатель видит свою настоящую роль, а не маску
        FractionType displayFraction = fraction;

        int leftPos = screen.getGuiLeft();
        int topPos  = screen.getGuiTop();
        int imageW  = screen.getXSize();

        int x = leftPos + imageW + GAP;
        int y = topPos;

        GuiGraphics gfx = event.getGuiGraphics();
        drawPaper(gfx, x, y, NOTE_W, NOTE_H);

        Font font = mc.font;
        final int INK       = 0xFF2A1810;
        final int INK_SOFT  = 0xFF3A2F20;
        final int INK_FADED = 0xFF6B5842;

        // ===== Заголовок: булавка слева, кикер справа от неё =====
        // Булавка нарисована drawPaper в (x+12, y+6).
        String kicker = "ЛИЧНОЕ ДЕЛО";
        gfx.drawString(font, kicker, x + 24, y + 5, INK_FADED, false);

        // Тонкая линия под заголовком
        gfx.fill(x + PAD, y + 17, x + NOTE_W - PAD, y + 18,
                (0x80 << 24) | (INK_FADED & 0xFFFFFF));

        // ===== ИГРОК =====
        gfx.drawString(font, "ИГРОК", x + PAD, y + 22, INK_FADED, false);
        String name = player.getGameProfile().getName();
        if (font.width(name) > NOTE_W - PAD * 2) {
            name = font.plainSubstrByWidth(name, NOTE_W - PAD * 2 - 6) + "…";
        }
        gfx.drawString(font, name, x + PAD, y + 32, INK, false);

        // Hand-drawn разделитель
        drawHandDivider(gfx, x + PAD, y + 44, NOTE_W - PAD * 2,
                (0x60 << 24) | (INK_FADED & 0xFFFFFF));

        // ===== РОЛЬ =====
        gfx.drawString(font, "РОЛЬ", x + PAD, y + 48, INK_FADED, false);
        String roleName = displayFraction == FractionType.NONE ? "не назначена" : displayFraction.displayName;
        int roleColor = displayFraction == FractionType.NONE
                ? INK_FADED
                : (0xFF000000 | (displayFraction.color & 0xFFFFFF));
        gfx.drawString(font, roleName, x + PAD, y + 58, roleColor, false);

        // ===== МАСКА (только предатель) =====
        boolean hasMask = displayFraction == FractionType.IMPOSTER && !mask.isEmpty();
        FractionType maskF = hasMask ? FractionType.fromName(mask) : null;
        int descTop;
        if (hasMask && maskF != null && maskF != FractionType.NONE) {
            gfx.drawString(font, "МАСКА", x + PAD, y + 70, INK_FADED, false);
            int mc2 = 0xFF000000 | (maskF.color & 0xFFFFFF);
            gfx.drawString(font, maskF.displayName, x + PAD, y + 80, mc2, false);
            descTop = y + 92;
        } else {
            descTop = y + 74;
        }

        // ===== ОПИСАНИЕ =====
        String desc = description(displayFraction);
        if (!desc.isEmpty()) {
            int maxW = NOTE_W - PAD * 2;
            // Резерв снизу — место под штамп (~16px)
            int descBottomLimit = y + NOTE_H - 18;
            String[] lines = wrap(font, desc, maxW);
            int dy = descTop;
            for (String line : lines) {
                if (dy + 9 > descBottomLimit) break;
                gfx.drawString(font, line, x + PAD, dy, INK_SOFT, false);
                dy += 10;
            }
        }

        // ===== ПЕЧАТЬ =====
        if (displayFraction != FractionType.NONE) {
            int stampColor = displayFraction == FractionType.IMPOSTER ? 0xCC9C1B1B : 0xCCA85A1F;
            String stamp = displayFraction == FractionType.IMPOSTER ? "СЕКРЕТНО" : "УЧТЕНО";
            int sw = font.width(stamp) + 8;
            int sh = 11;
            int sx = x + NOTE_W - sw - 5;
            int sy = y + NOTE_H - sh - 4;
            gfx.fill(sx, sy, sx + sw, sy + 1, stampColor);
            gfx.fill(sx, sy + sh - 1, sx + sw, sy + sh, stampColor);
            gfx.fill(sx, sy, sx + 1, sy + sh, stampColor);
            gfx.fill(sx + sw - 1, sy, sx + sw, sy + sh, stampColor);
            gfx.drawString(font, stamp, sx + 4, sy + 2, stampColor, false);
        }
    }

    /** Лёгкая волнистая линия — рукописный разделитель. */
    private static void drawHandDivider(GuiGraphics gfx, int x, int y, int w, int color) {
        for (int i = 0; i < w - 1; i++) {
            double t = i / (double) (w - 1);
            int dy = (int) Math.round(Math.sin(t * Math.PI * 2.0) * 0.8);
            gfx.fill(x + i, y + dy, x + i + 1, y + dy + 1, color);
        }
    }

    private static String description(FractionType f) {
        if (f == FractionType.NONE) return "ждём приказа.";
        return com.labyrinthmod.common.event.FractionEvents.getFractionDescription(f);
    }

    private static String[] wrap(Font font, String text, int maxW) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (font.width(trial) <= maxW) {
                cur.setLength(0);
                cur.append(trial);
            } else {
                if (cur.length() > 0) out.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static void drawPaper(GuiGraphics gfx, int x, int y, int w, int h) {
        // тень
        gfx.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x70000000);
        // лист
        gfx.fill(x, y, x + w, y + h, 0xFFE9DCB9);
        // пятна (стабильный seed)
        Random r = new Random(((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) w * 83492791L) ^ ((long) h));
        int spots = Math.max(6, (w * h) / 700);
        for (int i = 0; i < spots; i++) {
            int sx = x + 2 + r.nextInt(Math.max(1, w - 4));
            int sy = y + 2 + r.nextInt(Math.max(1, h - 4));
            int t = r.nextInt(3);
            int c = t == 0 ? 0xFFD4C5A0 : (t == 1 ? 0xFFB8A581 : 0xFF8B7355);
            gfx.fill(sx, sy, sx + 1, sy + 1, c);
        }
        // края
        for (int i = 0; i < 4; i++) {
            int a = (int) (40 * (1 - i / 4f));
            int c = (a << 24);
            gfx.fill(x, y + i, x + w, y + i + 1, c);
            gfx.fill(x, y + h - i - 1, x + w, y + h - i, c);
            gfx.fill(x + i, y, x + i + 1, y + h, c);
            gfx.fill(x + w - i - 1, y, x + w - i, y + h, c);
        }
        // булавка
        int cx = x + 12, cy = y + 6;
        gfx.fill(cx - 5, cy - 3, cx + 6, cy + 7, 0x60000000);
        gfx.fill(cx - 4, cy - 4, cx + 5, cy + 5, 0xFF8B1A1A);
        gfx.fill(cx - 3, cy - 3, cx - 1, cy - 1, 0xFFE84A4A);
        gfx.fill(cx - 2, cy - 2, cx - 1, cy - 1, 0xFFFFFFFF);
    }
}
