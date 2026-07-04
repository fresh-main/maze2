package com.infection.client.gui;

import com.infection.event.MiniEventType;
import com.infection.network.Network;
import com.infection.network.packet.C2SMiniEventActionPacket;
import com.infection.network.packet.S2CInfectionListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Меню инвентиков. Список доступных событий.
 *
 *  - JUMPSCARE: клик → SELECT на сервер → фаза подготовки → хоткей G запускает.
 *  - HALLUCINATION_SURGE: клик → открывает {@link HallucinationConfigScreen} с
 *    выбором конкретных игроков и фраз. Точечный, без радиуса, без PREPARING-фазы.
 */
public class MiniEventsScreen extends Screen {

    private static final int PANEL_W = 480;
    private static final int ROW_H = 56;

    private final Screen parent;
    /** Список онлайн-игроков, проброшенный из InfectionListScreen — нужен для HallucinationConfigScreen. */
    private final List<S2CInfectionListPacket.Entry> entries;

    public MiniEventsScreen(Screen parent, List<S2CInfectionListPacket.Entry> entries) {
        super(Component.literal("ИНВЕНТИКИ"));
        this.parent = parent;
        this.entries = entries == null ? Collections.emptyList() : entries;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = (this.width - PANEL_W) / 2;
        int rowY = 60;
        for (MiniEventType type : MiniEventType.values()) {
            int finalRowY = rowY;
            addRenderableWidget(Button.builder(
                            Component.literal(type.displayName).copy().append(" — выбрать"),
                            b -> select(type))
                    .bounds(panelX + 8, finalRowY + 8, PANEL_W - 16, 20)
                    .build());
            rowY += ROW_H;
        }

        addRenderableWidget(Button.builder(
                        Component.literal("Назад"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    private void select(MiniEventType type) {
        // Hallucination — открыть точечный конфигуратор с выбором конкретных игроков.
        if (type == MiniEventType.HALLUCINATION_SURGE) {
            this.minecraft.setScreen(new HallucinationConfigScreen(this, entries));
            return;
        }
        // Looming / Flicker / BLACK_RUSH — таргет-инвентики, выбираем одного игрока.
        if (type == MiniEventType.LOOMING
                || type == MiniEventType.FLICKER_PRESENCE
                || type == MiniEventType.BLACK_RUSH) {
            this.minecraft.setScreen(new SingleTargetSelectScreen(this, type, entries));
            return;
        }
        // SMOKE — без PREPARING. Клик активирует ивент: модель админа становится
        // чёрным силуэтом (видим). Внутри ивента: R — исчезнуть, T — появиться, B — выйти.
        if (type == MiniEventType.SMOKE) {
            Network.CHANNEL.sendToServer(new C2SMiniEventActionPacket(
                    C2SMiniEventActionPacket.ACTION_SMOKE_ACTIVATE, 0));
            this.minecraft.setScreen(null);
            return;
        }
        Network.CHANNEL.sendToServer(new C2SMiniEventActionPacket(
                C2SMiniEventActionPacket.ACTION_SELECT, type.ordinal()));
        // Закрываем экран — игрок должен видеть мир, чтобы спозиционировать чёрный силуэт.
        this.minecraft.setScreen(null);
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

        // Описание под кнопками
        int rowY = 60;
        for (MiniEventType type : MiniEventType.values()) {
            gfx.drawString(this.font, type.description, panelX + 8, rowY + 32,
                    0xFF888888, false);
            rowY += ROW_H;
        }

        // Подсказка снизу — клавиши берём через mc.options.keyMappings (а не только
        // из статической ссылки KeyBindings), чтобы при переназначении текст
        // показывал актуальную клавишу даже если Forge оставил наш static-ref несовместимым.
        String launchKey = liveKey(com.infection.client.KeyBindings.EVENT_LAUNCH);
        String cancelKey = liveKey(com.infection.client.KeyBindings.EVENT_CANCEL);
        String smokeHideKey = liveKey(com.infection.client.KeyBindings.EVENT_SMOKE_HIDE);
        String smokeShowKey = liveKey(com.infection.client.KeyBindings.EVENT_SMOKE_SHOW);

        String hint1 = "Jumpscare: клик → силуэт → «Запуск» (" + launchKey + ") или «Отмена» (" + cancelKey + ").";
        String hint2 = "Приступ: точечный — выбираешь игроков и сразу запускаешь.";
        String hint3 = "Дым: " + smokeHideKey + " — исчезнуть, " + smokeShowKey + " — появиться (модель чёрная).";
        gfx.drawString(this.font, hint1, panelX, this.height - 72, 0xFF999999, false);
        gfx.drawString(this.font, hint2, panelX, this.height - 60, 0xFF999999, false);
        gfx.drawString(this.font, hint3, panelX, this.height - 48, 0xFF999999, false);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Live-look-up клавиши маппинга через mc.options.keyMappings. */
    private static String liveKey(net.minecraft.client.KeyMapping mapping) {
        net.minecraft.client.KeyMapping live = mapping;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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
