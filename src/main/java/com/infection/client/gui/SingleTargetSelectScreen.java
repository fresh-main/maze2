package com.infection.client.gui;

import com.infection.event.MiniEventType;
import com.infection.network.Network;
import com.infection.network.packet.C2STargetedEventLaunchPacket;
import com.infection.network.packet.S2CInfectionListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Выбор ОДНОГО игрока для таргет-инвентика (LOOMING / FLICKER_PRESENCE).
 *
 * UX: каждый игрок — кнопка «ник — выбрать». Один клик = выбор + запуск + закрытие экрана.
 * Без флажков, без подтверждения — для админа быстро.
 */
public class SingleTargetSelectScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int LIST_TOP = 60;

    private final Screen parent;
    private final MiniEventType eventType;
    private final List<S2CInfectionListPacket.Entry> entries;
    private int scroll = 0;

    public SingleTargetSelectScreen(Screen parent, MiniEventType eventType,
                                    List<S2CInfectionListPacket.Entry> entries) {
        super(Component.literal(eventType.displayName.toUpperCase() + " · ВЫБОР ЦЕЛИ"));
        this.parent = parent;
        this.eventType = eventType;
        this.entries = entries == null ? Collections.emptyList() : entries;
    }

    @Override
    protected void init() {
        super.init();

        int btnW = 380;
        int btnX = (this.width - btnW) / 2;
        int listBottom = this.height - 40;
        int visibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_H);

        for (int i = 0; i < Math.min(visibleRows, entries.size()); i++) {
            int rowIdx = i + scroll;
            if (rowIdx >= entries.size()) break;
            S2CInfectionListPacket.Entry e = entries.get(rowIdx);
            int rowY = LIST_TOP + i * ROW_H;

            String label = e.name() + " (" + e.level() + "%) — запустить";
            addRenderableWidget(Button.builder(Component.literal(label),
                            b -> launch(e.id()))
                    .bounds(btnX, rowY, btnW, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Назад"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private void launch(java.util.UUID targetId) {
        Network.CHANNEL.sendToServer(new C2STargetedEventLaunchPacket(
                eventType.ordinal(), targetId));
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int listBottom = this.height - 40;
        int visibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_H);
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        int newScroll = (int) Math.max(0, Math.min(maxScroll, scroll - delta));
        if (newScroll != scroll) {
            scroll = newScroll;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        int panelW = 460;
        int panelX = (this.width - panelW) / 2;
        int panelY = 8;
        gfx.fill(panelX, panelY, panelX + panelW, this.height - 8, 0xD0101014);
        gfx.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF8B0E0E);
        gfx.fill(panelX, this.height - 9, panelX + panelW, this.height - 8, 0xFF8B0E0E);

        String t = this.title.getString();
        gfx.drawString(this.font, t, this.width / 2 - this.font.width(t) / 2, 18,
                0xFFFFE0E0, false);

        String hint = entries.isEmpty()
                ? "Список игроков пуст. Сначала открой админ-меню (V), потом «Инвентики»."
                : "Один клик по игроку — мгновенный запуск. Жертва получит эффект.";
        gfx.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 36,
                0xFF999999, false);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
