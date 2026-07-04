package com.infection.client.minievent;

import com.infection.client.KeyBindings;
import com.infection.event.MiniEventState;
import com.infection.event.MiniEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-подсказка со списком хоткеев активного мини-ивентика. Показывается ТОЛЬКО:
 *  • локальному игроку в creative режиме (обычные игроки не должны её видеть);
 *  • когда у этого игрока есть активная сессия мини-ивентика (PREPARING или ACTIVE).
 *
 * Содержимое подсказки зависит от типа активного ивентика — для SMOKE это R/T/B,
 * для JUMPSCARE — G/B и т.д.
 */
public final class EventHotkeysOverlay implements IGuiOverlay {

    public static final EventHotkeysOverlay INSTANCE = new EventHotkeysOverlay();

    private EventHotkeysOverlay() {}

    @Override
    public void render(net.minecraftforge.client.gui.overlay.ForgeGui gui,
                       GuiGraphics gfx,
                       float partialTick, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;

        // ТОЛЬКО для creative-игроков (это инструмент админа).
        if (!p.isCreative()) return;

        ClientMiniEventState.Snapshot s = ClientMiniEventState.get(p.getUUID());
        if (s == null || s.state() == MiniEventState.IDLE) return;

        List<String[]> entries = buildEntries(s.type());
        if (entries.isEmpty()) return;

        Font font = mc.font;

        // Высчитываем размер блока: заголовок + строки.
        String header = "[" + s.type().displayName + "]";
        int headerW = font.width(header);

        int rowH = 11;
        int padding = 6;
        int maxLineW = headerW;
        for (String[] e : entries) {
            int lineW = font.width(e[0] + "  " + e[1]);
            if (lineW > maxLineW) maxLineW = lineW;
        }

        int boxW = maxLineW + padding * 2;
        int boxH = padding * 2 + rowH + entries.size() * rowH;

        // Правый верхний угол, ниже статусов.
        int x = screenW - boxW - 6;
        int y = 6;

        // Фон.
        gfx.fill(x, y, x + boxW, y + boxH, 0xC0101014);
        // Тонкая красная рамка сверху и снизу.
        gfx.fill(x, y, x + boxW, y + 1, 0xFF8B0E0E);
        gfx.fill(x, y + boxH - 1, x + boxW, y + boxH, 0xFF8B0E0E);

        // Заголовок.
        gfx.drawString(font, header, x + padding, y + padding, 0xFFFF6464, false);

        // Строки.
        int ly = y + padding + rowH;
        for (String[] e : entries) {
            // Кей слева красным, описание справа серым.
            gfx.drawString(font, e[0], x + padding, ly, 0xFFFFCC44, false);
            int kw = font.width(e[0] + "  ");
            gfx.drawString(font, e[1], x + padding + kw, ly, 0xFFCCCCCC, false);
            ly += rowH;
        }
    }

    /** Список (key, label) для конкретного типа ивентика. */
    private List<String[]> buildEntries(MiniEventType type) {
        List<String[]> out = new ArrayList<>();
        switch (type) {
            case SMOKE -> {
                out.add(new String[]{key(KeyBindings.EVENT_SMOKE_HIDE), "Исчезнуть в дыму"});
                out.add(new String[]{key(KeyBindings.EVENT_SMOKE_SHOW), "Появиться из дыма"});
                out.add(new String[]{key(KeyBindings.EVENT_CANCEL), "Выйти из ивента"});
            }
            case JUMPSCARE -> {
                out.add(new String[]{key(KeyBindings.EVENT_LAUNCH), "Запустить"});
                out.add(new String[]{key(KeyBindings.EVENT_CANCEL), "Отменить"});
            }
            // LOOMING / FLICKER / BLACK_RUSH / HALLUCINATION_SURGE — без PREPARING,
            // запускаются автоматически. Полезно показать только Cancel.
            default -> out.add(new String[]{key(KeyBindings.EVENT_CANCEL), "Отменить"});
        }
        return out;
    }

    /**
     * Возвращает actual-текущую клавишу маппинга. Раньше использовался
     * {@code getTranslatedKeyMessage()} — но в каких-то редких случаях
     * (Forge не вызывает resetMapping после рестарта/перезагрузки) сюда
     * прилетал старый key. Идём через MC options и ищем маппинг по name —
     * это гарантирует, что мы видим то же, что и контролы.
     */
    private static String key(net.minecraft.client.KeyMapping mapping) {
        net.minecraft.client.KeyMapping live = mapping;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.keyMappings != null) {
            for (net.minecraft.client.KeyMapping km : mc.options.keyMappings) {
                if (km != null && mapping.getName().equals(km.getName())) {
                    live = km;
                    break;
                }
            }
        }
        var k = live.getKey();
        if (k == null || k == com.mojang.blaze3d.platform.InputConstants.UNKNOWN) return "?";
        return k.getDisplayName().getString().toUpperCase();
    }
}
