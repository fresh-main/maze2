package com.infection.client.gui;

import com.infection.network.Network;
import com.infection.network.packet.C2SUpdateSettingsPacket;
import com.infection.settings.ClientSettings;
import com.infection.settings.InfectionSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * Админ-меню правки настроек заражения. Сохранение шлёт C2SUpdateSettingsPacket;
 * сервер проверяет OP, обновляет SavedData и рассылает S2CSettingsSyncPacket всем.
 */
public class InfectionSettingsScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int ROW_H = 24;

    private final Screen parent;

    private final List<Row> rows = new ArrayList<>();

    public InfectionSettingsScreen(Screen parent) {
        super(Component.literal("НАСТРОЙКИ ЗАРАЖЕНИЯ"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        rows.clear();
        InfectionSettings s = ClientSettings.get();

        int topY = 40;
        int panelX = (this.width - PANEL_W) / 2;
        int labelW = 280;
        int fieldX = panelX + labelW + 6;
        int fieldW = 80;

        int y = topY;

        addRow("min_level — % с которого идут галлюцинации", s.minLevel,
                (res, v) -> res.minLevel = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("terminal_start_level — % c которого интервал = терминальный", s.terminalStartLevel,
                (res, v) -> res.terminalStartLevel = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("start_interval (тики) — интервал на min_level", s.startIntervalTicks,
                (res, v) -> res.startIntervalTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("terminal_interval (тики) — интервал на 100%+", s.terminalIntervalTicks,
                (res, v) -> res.terminalIntervalTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("reduction_per_percent (тики) — спад за каждый %", s.reductionPerPercentTicks,
                (res, v) -> res.reductionPerPercentTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("attack_min_duration (тики)", s.attackMinDurationTicks,
                (res, v) -> res.attackMinDurationTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("attack_max_duration (тики)", s.attackMaxDurationTicks,
                (res, v) -> res.attackMaxDurationTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("growth_interval (тики на +1%) — 0 выкл.", s.growthIntervalTicks,
                (res, v) -> res.growthIntervalTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("terminal_damage_interval (тики) — 0 без смерти", s.terminalDamageIntervalTicks,
                (res, v) -> res.terminalDamageIntervalTicks = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        addRow("ambience_start_level (%) — с какого % играет эмбиент (101 = выкл.)", s.ambienceStartLevel,
                (res, v) -> res.ambienceStartLevel = v, panelX, y, labelW, fieldX, fieldW); y += ROW_H;

        int btnY = y + 12;
        addRenderableWidget(Button.builder(Component.literal("Сохранить"), b -> applyAndClose())
                .bounds(this.width / 2 - 104, btnY, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"), b -> onClose())
                .bounds(this.width / 2 + 4, btnY, 100, 20).build());
    }

    private void addRow(String label, int initial,
                        BiConsumer<InfectionSettings, Integer> setter,
                        int panelX, int y, int labelW, int fieldX, int fieldW) {
        EditBox box = new EditBox(this.font, fieldX, y + 2, fieldW, 18, Component.literal(label));
        box.setMaxLength(8);
        box.setValue(Integer.toString(initial));
        addRenderableWidget(box);
        rows.add(new Row(label, panelX, y, labelW, box, setter));
    }

    private void applyAndClose() {
        InfectionSettings fresh = ClientSettings.get().copy();
        for (Row r : rows) {
            try {
                int v = Integer.parseInt(r.box.getValue().trim());
                r.setter.accept(fresh, v);
            } catch (NumberFormatException ignored) {
                // оставим предыдущее значение
            }
        }
        fresh.clampAll();
        Network.CHANNEL.sendToServer(new C2SUpdateSettingsPacket(fresh));
        onClose();
    }

    @Override
    public void onClose() {
        if (parent != null) {
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        int panelX = (this.width - PANEL_W) / 2;
        int panelY = 8;
        gfx.fill(panelX - 8, panelY, panelX + PANEL_W + 8, this.height - 8, 0xD0101014);
        gfx.fill(panelX - 8, panelY, panelX + PANEL_W + 8, panelY + 1, 0xFF8B0E0E);
        gfx.fill(panelX - 8, this.height - 9, panelX + PANEL_W + 8, this.height - 8, 0xFF8B0E0E);

        String title = this.title.getString();
        int tw = this.font.width(title);
        gfx.drawString(this.font, title, this.width / 2 - tw / 2, 18, 0xFFDDDDDD, false);

        for (Row r : rows) {
            gfx.drawString(this.font, r.label, r.x, r.y + 6, 0xFFB0B0B0, false);
        }

        super.render(gfx, mouseX, mouseY, partialTick);

        // Подсказка с рассчитанными значениями.
        int infoY = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y + ROW_H + 40;
        gfx.drawString(this.font, "(1 секунда = 20 тиков. 300 = 15 сек, 6000 = 5 мин.)",
                panelX, infoY, 0xFF777777, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Row(String label, int x, int y, int labelW,
                       EditBox box, BiConsumer<InfectionSettings, Integer> setter) {}

    // suppress unused helper if someone wants typed extraction
    @SuppressWarnings("unused")
    private static int read(EditBox box, ToIntFunction<String> parse, int fallback) {
        try {
            return parse.applyAsInt(box.getValue().trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
