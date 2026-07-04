package com.infection.client.gui;

import com.infection.capability.InfectionStage;
import com.infection.network.Network;
import com.infection.network.packet.C2SInfectionActionPacket;
import com.infection.network.packet.C2SRequestInfectionListPacket;
import com.infection.network.packet.S2CInfectionListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Панель контроля заражения. Показывает список онлайн-игроков с процентом заражения,
 * позволяет заражать/лечить, открывать редактор записки.
 */
public class InfectionListScreen extends Screen {

    private static final int ROW_H = 26;
    private static final int PANEL_W = 480;
    private static final int TOP_PAD = 40;
    private static final int BOTTOM_PAD = 40;

    private final List<S2CInfectionListPacket.Entry> entries;
    private int scroll = 0;

    public InfectionListScreen(List<S2CInfectionListPacket.Entry> entries) {
        super(Component.translatable("infection.gui.title"));
        this.entries = entries;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = (this.width - PANEL_W) / 2;
        int listTop = TOP_PAD;
        int listBottom = this.height - BOTTOM_PAD;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);

        for (int i = 0; i < Math.min(visibleRows, entries.size()); i++) {
            int rowIdx = i + scroll;
            if (rowIdx >= entries.size()) break;
            S2CInfectionListPacket.Entry e = entries.get(rowIdx);
            int rowY = listTop + i * ROW_H;

            // Кнопка точного редактора — открывает экран с полями уровня/множителя/интервала
            // и быстрыми действиями (Полное заражение, Запустить рост, Стоп, Вылечить).
            addRenderableWidget(Button.builder(Component.literal("Настроить"),
                            b -> this.minecraft.setScreen(new InfectionPlayerEditScreen(this, e)))
                    .bounds(panelX + PANEL_W - 240, rowY + 2, 76, 20).build());

            addRenderableWidget(Button.builder(Component.translatable("infection.gui.infect"),
                            b -> sendAction(e.id(), C2SInfectionActionPacket.ACTION_FULL, 0))
                    .bounds(panelX + PANEL_W - 160, rowY + 2, 76, 20).build());

            addRenderableWidget(Button.builder(Component.translatable("infection.gui.note"),
                            b -> openNoteEditor(e))
                    .bounds(panelX + PANEL_W - 80, rowY + 2, 76, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Настройки"),
                        b -> this.minecraft.setScreen(new InfectionSettingsScreen(this)))
                .bounds(this.width / 2 - 260, this.height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Инвентики"),
                        b -> this.minecraft.setScreen(new MiniEventsScreen(this, entries)))
                .bounds(this.width / 2 - 156, this.height - 28, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("infection.gui.close"),
                        b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private void openNoteEditor(S2CInfectionListPacket.Entry e) {
        this.minecraft.setScreen(new AdminNoteEditorScreen(this, e.id(), e.name(), e.customNoteText()));
    }

    private void sendAction(UUID target, int action, int value) {
        Network.CHANNEL.sendToServer(new C2SInfectionActionPacket(target, action, value));
        Network.CHANNEL.sendToServer(new C2SRequestInfectionListPacket());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int visibleRows = Math.max(1, (this.height - TOP_PAD - BOTTOM_PAD) / ROW_H);
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

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = 8;
        gfx.fill(panelX - 8, panelY, panelX + PANEL_W + 8, this.height - 8, 0xD0101014);
        gfx.fill(panelX - 8, panelY, panelX + PANEL_W + 8, panelY + 1, 0xFF8B0E0E);
        gfx.fill(panelX - 8, this.height - 9, panelX + PANEL_W + 8, this.height - 8, 0xFF8B0E0E);

        // Заголовок
        String title = this.title.getString();
        int tw = this.font.width(title);
        gfx.drawString(this.font, title, this.width / 2 - tw / 2, 18, 0xFFDDDDDD, false);

        // Подсчёт
        String online = Component.translatable("infection.gui.online", entries.size()).getString();
        gfx.drawString(this.font, online, panelX, 28, 0xFF999999, false);

        int listTop = TOP_PAD;
        int listBottom = this.height - BOTTOM_PAD;
        int visibleRows = Math.max(1, (listBottom - listTop) / ROW_H);

        for (int i = 0; i < Math.min(visibleRows, entries.size()); i++) {
            int rowIdx = i + scroll;
            if (rowIdx >= entries.size()) break;
            S2CInfectionListPacket.Entry e = entries.get(rowIdx);
            int rowY = listTop + i * ROW_H;

            // Подложка
            gfx.fill(panelX, rowY, panelX + PANEL_W, rowY + ROW_H - 2, 0xFF1A1A1A);

            // Прогресс-бар стадии
            InfectionStage stage = InfectionStage.fromLevel(e.level());
            int barW = 100;
            int filled = (int) (barW * (e.level() / 100f));
            gfx.fill(panelX + 6, rowY + 16, panelX + 6 + barW, rowY + 20, 0xFF2A2A2A);
            int barColor = colorForStage(stage);
            gfx.fill(panelX + 6, rowY + 16, panelX + 6 + filled, rowY + 20, barColor);

            // Ник + маркер кастомной записки (мелкий «*»)
            boolean hasCustom = e.customNoteText() != null && !e.customNoteText().isEmpty();
            String name = hasCustom ? "* " + e.name() : e.name();
            int nameColor = hasCustom ? 0xFFE04A2A : 0xFFE0E0E0;
            gfx.drawString(this.font, name, panelX + 6, rowY + 4, nameColor, false);

            // Процент + название стадии
            String pct = e.level() + "%";
            gfx.drawString(this.font, pct, panelX + 6 + barW + 10, rowY + 14, barColor, false);
            gfx.drawString(this.font, stage.name(), panelX + 6 + barW + 50, rowY + 14, 0xFF888888, false);
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private static int colorForStage(InfectionStage s) {
        return switch (s) {
            case CLEAN -> 0xFF3A8A3A;
            case TRACE -> 0xFF8A8A3A;
            case EARLY -> 0xFFB8892F;
            case ACTIVE -> 0xFFC86A2A;
            case HEAVY -> 0xFFE04A2A;
            case CRITICAL -> 0xFFB82A2A;
            case TERMINAL -> 0xFF6A0A0A;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
