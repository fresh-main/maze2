package com.infection.client.gui;

import com.infection.network.Network;
import com.infection.network.packet.C2SAdminSetNotePacket;
import com.infection.network.packet.C2SRequestInfectionListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Админ-экран редактирования КАСТОМНОЙ части записки игрока.
 * Авто-фраза по стадии (noteText) пишется отдельно сервером и здесь не редактируется.
 *
 * Кастомный текст показывается на той же карточке-записке игрока ниже авто-фразы.
 * Через эту форму админ может вписывать что угодно (шизофрения-эффект).
 */
public class AdminNoteEditorScreen extends Screen {

    private static final int BOX_W = 380;
    private static final int BOX_H = 180;

    private final Screen parent;
    private final UUID target;
    private final String targetName;
    private final String initialText;

    private MultiLineEditBox editBox;

    public AdminNoteEditorScreen(Screen parent, UUID target, String targetName, String initialCustomText) {
        super(Component.literal("Редактор записки: " + targetName));
        this.parent = parent;
        this.target = target;
        this.targetName = targetName;
        this.initialText = initialCustomText == null ? "" : initialCustomText;
    }

    @Override
    protected void init() {
        super.init();

        int boxX = (this.width - BOX_W) / 2;
        int boxY = 50;

        editBox = new MultiLineEditBox(this.font, boxX, boxY, BOX_W, BOX_H,
                Component.literal("Кастомный текст"),
                Component.literal("Кастомный текст"));
        editBox.setValue(initialText);
        editBox.setCharacterLimit(4000);
        addRenderableWidget(editBox);

        int btnY = boxY + BOX_H + 10;

        addRenderableWidget(Button.builder(Component.literal("Сохранить"),
                        b -> save(false))
                .bounds(boxX, btnY, 180, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Сбросить (очистить)"),
                        b -> save(true))
                .bounds(boxX + 190, btnY, 180, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal("Стереть кастомный текст. Авто-самочувствие останется.")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("Отмена"),
                        b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 50, btnY + 26, 100, 20).build());
    }

    private void save(boolean reset) {
        String text = reset ? "" : editBox.getValue();
        Network.CHANNEL.sendToServer(new C2SAdminSetNotePacket(target, text, reset));
        // обновим список (сервер пришлёт свежий S2CInfectionListPacket)
        Network.CHANNEL.sendToServer(new C2SRequestInfectionListPacket());
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx);

        // заголовок
        String t = this.title.getString();
        gfx.drawString(this.font, t, this.width / 2 - this.font.width(t) / 2, 16, 0xFFFFE0E0, false);

        // подсказка
        String hint = "Авто-самочувствие по стадии остаётся. Это — отдельный, ваш текст.";
        gfx.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, 30,
                0xFF999999, false);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
