package com.infection.client.gui;

import com.infection.network.Network;
import com.infection.network.packet.C2SHallucinationLaunchPacket;
import com.infection.network.packet.S2CInfectionListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Точечная настройка инвентика «Приступ».
 *
 * Слева — список онлайн-игроков с чекбоксами (admin отмечает целей точечно, без радиуса).
 * Можно выбрать одного, нескольких, всех — без ограничений по дистанции и измерению.
 *
 * Справа — поле собственных фраз (одна строка = одна фраза) + чекбокс «дефолтные тоже».
 *
 * Кнопка «Запустить» шлёт {@link C2SHallucinationLaunchPacket} с UUID-целей и фразами.
 * Сервер сразу шлёт force-hallucination каждому из выбранных игроков.
 */
public class HallucinationConfigScreen extends Screen {

    private static final int PLAYER_COL_W = 220;
    private static final int PHRASE_COL_W = 380;
    private static final int ROW_H = 22;
    private static final int LIST_TOP = 60;

    private final Screen parent;
    private final List<S2CInfectionListPacket.Entry> entries;
    private final Set<UUID> selected = new HashSet<>();

    private MultiLineEditBox phrasesBox;
    private Checkbox useDefaultsBox;
    private int scroll = 0;

    public HallucinationConfigScreen(Screen parent, List<S2CInfectionListPacket.Entry> entries) {
        super(Component.literal("ПРИСТУП · ТОЧЕЧНЫЙ ЗАПУСК"));
        this.parent = parent;
        this.entries = entries == null ? Collections.emptyList() : entries;
    }

    @Override
    protected void init() {
        super.init();

        // ===== Левая колонка: список игроков с чекбоксами =====
        int leftX = this.width / 2 - PLAYER_COL_W - 12;
        int listBottom = this.height - 64;
        int visibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_H);

        for (int i = 0; i < Math.min(visibleRows, entries.size()); i++) {
            int rowIdx = i + scroll;
            if (rowIdx >= entries.size()) break;
            S2CInfectionListPacket.Entry e = entries.get(rowIdx);
            int rowY = LIST_TOP + i * ROW_H;

            Checkbox cb = new Checkbox(leftX, rowY, PLAYER_COL_W, 18,
                    Component.literal(e.name() + " (" + e.level() + "%)"),
                    selected.contains(e.id())) {
                @Override
                public void onPress() {
                    super.onPress();
                    Set<UUID> sel = HallucinationConfigScreen.this.selected;
                    if (sel.contains(e.id())) sel.remove(e.id());
                    else sel.add(e.id());
                }
            };
            addRenderableWidget(cb);
        }

        // Кнопки группового выбора
        addRenderableWidget(Button.builder(Component.literal("Все"),
                        b -> { selected.clear(); for (var e : entries) selected.add(e.id()); rebuildWidgets(); })
                .bounds(leftX, listBottom + 4, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Никого"),
                        b -> { selected.clear(); rebuildWidgets(); })
                .bounds(leftX + 76, listBottom + 4, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Инверт."),
                        b -> {
                            Set<UUID> inv = new HashSet<>();
                            for (var e : entries) if (!selected.contains(e.id())) inv.add(e.id());
                            selected.clear(); selected.addAll(inv); rebuildWidgets();
                        })
                .bounds(leftX + 152, listBottom + 4, 64, 18).build());

        // ===== Правая колонка: фразы + чекбокс =====
        int rightX = this.width / 2 + 12;
        int phrasesH = listBottom - LIST_TOP - 28;
        phrasesBox = new MultiLineEditBox(this.font, rightX, LIST_TOP, PHRASE_COL_W, phrasesH,
                Component.literal("Свои фразы (по одной на строку)"),
                Component.literal("Свои фразы (по одной на строку)"));
        phrasesBox.setCharacterLimit(4000);
        addRenderableWidget(phrasesBox);

        useDefaultsBox = new Checkbox(rightX, LIST_TOP + phrasesH + 4, PHRASE_COL_W, 18,
                Component.literal("Использовать стандартные фразы тоже"),
                true);
        addRenderableWidget(useDefaultsBox);

        // ===== Низ: запуск + отмена =====
        int btnY = this.height - 28;
        addRenderableWidget(Button.builder(Component.literal("Запустить приступ"),
                        b -> launch())
                .bounds(this.width / 2 - 152, btnY, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отмена"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 + 12, btnY, 140, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int leftX = this.width / 2 - PLAYER_COL_W - 12;
        if (mx >= leftX && mx <= leftX + PLAYER_COL_W) {
            int listBottom = this.height - 64;
            int visibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_H);
            int maxScroll = Math.max(0, entries.size() - visibleRows);
            int newScroll = (int) Math.max(0, Math.min(maxScroll, scroll - delta));
            if (newScroll != scroll) {
                scroll = newScroll;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void launch() {
        if (selected.isEmpty()) {
            // ничего не выбрано — действие отменяется silently, кнопка не работает
            return;
        }
        String raw = phrasesBox.getValue();
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\n", -1)) {
            String t = line.trim();
            if (!t.isEmpty()) lines.add(t);
        }
        boolean useDefaults = useDefaultsBox.selected();
        if (lines.isEmpty() && !useDefaults) useDefaults = true;

        Network.CHANNEL.sendToServer(new C2SHallucinationLaunchPacket(
                useDefaults, lines, new ArrayList<>(selected)));

        // Закрываем — приступ уже летит к игрокам.
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        // Затемнение-панель
        int panelX = 16;
        int panelY = 8;
        gfx.fill(panelX, panelY, this.width - panelX, this.height - 8, 0xD0101014);
        gfx.fill(panelX, panelY, this.width - panelX, panelY + 1, 0xFF8B0E0E);
        gfx.fill(panelX, this.height - 9, this.width - panelX, this.height - 8, 0xFF8B0E0E);

        // Заголовок
        String t = this.title.getString();
        gfx.drawString(this.font, t, this.width / 2 - this.font.width(t) / 2, 18, 0xFFFFE0E0, false);

        // Подсказка
        String hint = entries.isEmpty()
                ? "Список игроков пуст. Открой админ-меню (V) → «Инвентики» — список придёт с сервера."
                : "Отметь цели слева. Свои фразы — справа. Точечно, без радиуса.";
        gfx.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 32,
                0xFF999999, false);

        // Заголовки колонок
        int leftX = this.width / 2 - PLAYER_COL_W - 12;
        int rightX = this.width / 2 + 12;
        gfx.drawString(this.font, "ЦЕЛИ (" + selected.size() + "/" + entries.size() + ")",
                leftX, LIST_TOP - 12, 0xFFB0B0B0, false);
        gfx.drawString(this.font, "ФРАЗЫ", rightX, LIST_TOP - 12, 0xFFB0B0B0, false);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
