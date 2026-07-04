package com.infection.client.gui;

import com.infection.network.Network;
import com.infection.network.packet.C2SInfectionActionPacket;
import com.infection.network.packet.C2SRequestInfectionListPacket;
import com.infection.network.packet.S2CInfectionListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Редактор персональных параметров заражения для конкретного игрока.
 *
 * Показывает поля точного ввода и кнопки быстрых действий. Все значения хранятся
 * на сервере (capability у игрока). Админ может задать:
 *   • точный уровень заражения (0..100)
 *   • множитель скорости (0..3000% где 100% = базовая скорость)
 *   • персональный интервал роста (в секундах; 0 = использовать глобальный из настроек)
 *
 * Быстрые действия:
 *   • «Полное заражение» — выставить 100%
 *   • «Запустить рост»   — поднять до minLevel и multiplier=100% (если стоит на 0)
 *   • «Остановить»       — multiplier=0
 *   • «Вылечить»         — level=0, multiplier=100%
 */
public class InfectionPlayerEditScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int ROW_H = 26;
    private static final int LABEL_W = 220;

    private final Screen parent;
    private final UUID targetId;
    private final String targetName;

    private EditBox levelBox;
    private EditBox multiplierBox;
    private EditBox intervalSecondsBox;

    public InfectionPlayerEditScreen(Screen parent, S2CInfectionListPacket.Entry e) {
        super(Component.literal(e.name() + " · НАСТРОЙКИ ЗАРАЖЕНИЯ"));
        this.parent = parent;
        this.targetId = e.id();
        this.targetName = e.name();

        // Начальные значения берём из entry — приходят с актуального состояния сервера.
        this.levelBox = null; // создадим в init
    }

    @Override
    protected void init() {
        super.init();

        // Берём свежие значения из последнего ответа сервера через cache (если есть)
        // — но сначала создаём поля на init, конкретные значения подставит initText().
        int panelX = (this.width - PANEL_W) / 2;
        int fieldX = panelX + LABEL_W + 8;
        int fieldW = 70;

        int y = 56;

        levelBox = new EditBox(this.font, fieldX, y + 2, fieldW, 18,
                Component.literal("Уровень"));
        levelBox.setMaxLength(3);
        levelBox.setValue(initialLevel());
        addRenderableWidget(levelBox);
        y += ROW_H;

        multiplierBox = new EditBox(this.font, fieldX, y + 2, fieldW, 18,
                Component.literal("Множитель"));
        multiplierBox.setMaxLength(5);
        multiplierBox.setValue(initialMultiplier());
        addRenderableWidget(multiplierBox);
        y += ROW_H;

        intervalSecondsBox = new EditBox(this.font, fieldX, y + 2, fieldW, 18,
                Component.literal("Интервал"));
        intervalSecondsBox.setMaxLength(7);
        intervalSecondsBox.setValue(initialIntervalSeconds());
        addRenderableWidget(intervalSecondsBox);
        y += ROW_H;

        // Кнопка «Применить» (для трёх полей — единое сохранение)
        addRenderableWidget(Button.builder(Component.literal("Применить значения"),
                        b -> applyFields())
                .bounds(panelX, y + 6, PANEL_W, 20).build());
        y += 32;

        // Быстрые действия — два ряда по две кнопки
        int btnW = (PANEL_W - 8) / 2;
        addRenderableWidget(Button.builder(Component.literal("Полное заражение"),
                        b -> sendAction(C2SInfectionActionPacket.ACTION_FULL, 0))
                .bounds(panelX, y, btnW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Запустить рост"),
                        b -> sendAction(C2SInfectionActionPacket.ACTION_START, 0))
                .bounds(panelX + btnW + 8, y, btnW, 22).build());
        y += 28;
        addRenderableWidget(Button.builder(Component.literal("Остановить"),
                        b -> sendAction(C2SInfectionActionPacket.ACTION_STOP, 0))
                .bounds(panelX, y, btnW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Вылечить"),
                        b -> sendAction(C2SInfectionActionPacket.ACTION_CURE, 0))
                .bounds(panelX + btnW + 8, y, btnW, 22).build());

        // Назад
        addRenderableWidget(Button.builder(Component.literal("Назад"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private String initialLevel() {
        var e = com.infection.client.ClientInfectionCache.getEntry(targetId);
        return Integer.toString(e.level());
    }

    private String initialMultiplier() {
        var e = com.infection.client.ClientInfectionCache.getEntry(targetId);
        return Integer.toString(Math.round(e.growthMultiplier() * 100));
    }

    private String initialIntervalSeconds() {
        var e = com.infection.client.ClientInfectionCache.getEntry(targetId);
        // 0 = использовать глобальный
        if (e.personalGrowthIntervalTicks() <= 0) return "0";
        return Integer.toString(e.personalGrowthIntervalTicks() / 20);
    }

    private void applyFields() {
        // Уровень
        try {
            int lvl = Integer.parseInt(levelBox.getValue().trim());
            sendAction(C2SInfectionActionPacket.ACTION_SET, lvl);
        } catch (NumberFormatException ignored) {}

        // Множитель в % → шлём как value*1, на сервере делится на 100
        try {
            int pct = Integer.parseInt(multiplierBox.getValue().trim());
            sendAction(C2SInfectionActionPacket.ACTION_SET_MULTIPLIER, pct);
        } catch (NumberFormatException ignored) {}

        // Интервал в секундах → ticks
        try {
            int sec = Integer.parseInt(intervalSecondsBox.getValue().trim());
            int ticks = Math.max(0, sec) * 20;
            sendAction(C2SInfectionActionPacket.ACTION_SET_PERSONAL_INTERVAL, ticks);
        } catch (NumberFormatException ignored) {}
    }

    private void sendAction(int action, int value) {
        Network.CHANNEL.sendToServer(new C2SInfectionActionPacket(targetId, action, value));
        // Сразу запросим обновлённый список — после возвращения в parent отрисуется свежее.
        Network.CHANNEL.sendToServer(new C2SRequestInfectionListPacket());
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
        gfx.drawString(this.font, title,
                this.width / 2 - this.font.width(title) / 2, 18, 0xFFFFE0E0, false);

        // Метки рядом с полями (вычисляем те же координаты что в init)
        int y = 56;
        gfx.drawString(this.font, "Уровень заражения (0..100)",
                panelX, y + 6, 0xFFB0B0B0, false);
        y += ROW_H;
        gfx.drawString(this.font, "Множитель скорости (% от базовой; 0 = стоп)",
                panelX, y + 6, 0xFFB0B0B0, false);
        y += ROW_H;
        gfx.drawString(this.font, "Интервал +1% (сек; 0 = из настроек)",
                panelX, y + 6, 0xFFB0B0B0, false);

        super.render(gfx, mouseX, mouseY, partialTick);

        // Подсказка снизу
        int hintY = this.height - 50;
        gfx.drawString(this.font,
                "100% × 60 сек = +1% каждую минуту. 1000% × 60 сек = +1% за 6 сек.",
                panelX, hintY, 0xFF777777, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
