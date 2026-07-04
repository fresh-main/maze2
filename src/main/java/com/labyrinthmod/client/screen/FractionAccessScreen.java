package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.C2SFractionAccessUpdatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * GUI настройки «кто из фракций может выходить из safe-зоны (т.е. в лабиринт)».
 * Открывается админом из {@code AdminScreen} → кнопка «Доступ фракций» или
 * через автоматический ответ сервера на {@code C2SRequestFractionAccessPacket}.
 *
 * Для каждой фракции — строка с цветным именем и тогл-кнопкой:
 *   зелёный «✓ Может выходить» / красный «✗ Заперт».
 * Клик мгновенно шлёт {@link C2SFractionAccessUpdatePacket} серверу;
 * сервер бродкастит обновление всем операторам онлайн (тогл синхронен).
 */
@OnlyIn(Dist.CLIENT)
public class FractionAccessScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int PANEL_W = 360;

    private final Screen parent;
    private final Map<FractionType, Boolean> state = new EnumMap<>(FractionType.class);
    private final Map<FractionType, Button> rowButtons = new EnumMap<>(FractionType.class);

    public FractionAccessScreen(Screen parent, Map<FractionType, Boolean> initial) {
        super(Component.literal("Доступ фракций в лабиринт"));
        this.parent = parent;
        if (initial != null) state.putAll(initial);
    }

    /** Применить новый снапшот, пришедший от сервера (после изменения другим админом). */
    public void applySnapshot(Map<FractionType, Boolean> snap) {
        if (snap == null) return;
        state.clear();
        state.putAll(snap);
        // Обновляем подписи кнопок (если уже инициализирован UI).
        for (Map.Entry<FractionType, Button> e : rowButtons.entrySet()) {
            e.getValue().setMessage(buttonLabel(e.getKey()));
        }
    }

    @Override
    protected void init() {
        super.init();
        rowButtons.clear();

        int panelX = (this.width - PANEL_W) / 2;
        int rowY = 60;

        for (FractionType f : FractionType.values()) {
            // NONE и IMPOSTER — фракции с особой логикой; всё равно показываем,
            // чтобы админ мог настроить (например запретить NONE-игрокам выходить).
            int finalRowY = rowY;
            int btnW = 200;
            int nameW = PANEL_W - btnW - 16;

            // Кнопка-тогл.
            FractionType captured = f;
            Button btn = Button.builder(
                            buttonLabel(f),
                            b -> toggle(captured))
                    .bounds(panelX + nameW + 8, finalRowY, btnW, 20)
                    .build();
            addRenderableWidget(btn);
            rowButtons.put(f, btn);

            rowY += ROW_H;
        }

        addRenderableWidget(Button.builder(
                        Component.literal("Назад"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    private Component buttonLabel(FractionType f) {
        boolean canLeave = state.getOrDefault(f, false);
        return Component.literal(canLeave ? "✓ Может выходить" : "✗ Заперт в зоне");
    }

    private void toggle(FractionType f) {
        boolean newValue = !state.getOrDefault(f, false);
        state.put(f, newValue);
        Button b = rowButtons.get(f);
        if (b != null) b.setMessage(buttonLabel(f));
        NetworkHandler.CHANNEL.sendToServer(
                new C2SFractionAccessUpdatePacket(f.name(), newValue));
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        int panelX = (this.width - PANEL_W) / 2;
        int panelTop = 8;
        gfx.fill(panelX - 8, panelTop, panelX + PANEL_W + 8, this.height - 8, 0xD0101014);
        gfx.fill(panelX - 8, panelTop, panelX + PANEL_W + 8, panelTop + 1, 0xFF8B0E0E);
        gfx.fill(panelX - 8, this.height - 9, panelX + PANEL_W + 8, this.height - 8, 0xFF8B0E0E);

        String title = this.title.getString();
        int tw = this.font.width(title);
        gfx.drawString(this.font, title, this.width / 2 - tw / 2, 18, 0xFFDDDDDD, false);

        gfx.drawString(this.font,
                "Кто из фракций может покидать safe-зону и выходить в лабиринт.",
                panelX, 36, 0xFF999999, false);
        gfx.drawString(this.font,
                "Запрет = игрок этой фракции выкидывается обратно при попытке.",
                panelX, 46, 0xFF999999, false);

        // Имена фракций — отдельным столбцом слева.
        int rowY = 60;
        for (FractionType f : FractionType.values()) {
            int color = 0xFF000000 | (f.color & 0xFFFFFF);
            gfx.drawString(this.font, f.displayName, panelX + 4, rowY + 6, color, false);
            rowY += ROW_H;
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
