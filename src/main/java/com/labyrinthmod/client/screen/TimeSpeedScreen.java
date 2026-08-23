package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.event.TimeSpeedHandler;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.TimeSpeedPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TimeSpeedScreen extends Screen {

    private TimeSpeedSlider speedSlider;
    private Button toggleButton;
    private double currentSpeed;
    private boolean isEnabled;

    public TimeSpeedScreen() {
        super(Component.literal("Управление скоростью времени"));
        // Получаем текущие значения при открытии
        this.currentSpeed = TimeSpeedHandler.getTimeSpeed();
        this.isEnabled = TimeSpeedHandler.isEnabled();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Ползунок скорости (от 0.1 до 10.0)
        speedSlider = new TimeSpeedSlider(
                centerX - 150, centerY - 30,
                300, 20,
                0.1,   // минимальное значение
                10.0,  // максимальное значение
                currentSpeed, // текущее значение
                value -> {
                    currentSpeed = value;
                    TimeSpeedHandler.setTimeSpeed(currentSpeed);
                    // Отправляем на сервер
                    NetworkHandler.CHANNEL.sendToServer(new TimeSpeedPacket(currentSpeed, isEnabled));
                }
        );
        this.addRenderableWidget(speedSlider);

        // Кнопка включения/выключения
        toggleButton = Button.builder(
                Component.literal(isEnabled ? "§aВКЛ" : "§cВЫКЛ"),
                btn -> {
                    isEnabled = !isEnabled;
                    TimeSpeedHandler.setEnabled(isEnabled);
                    btn.setMessage(Component.literal(isEnabled ? "§aВКЛ" : "§cВЫКЛ"));
                    // Отправляем на сервер
                    NetworkHandler.CHANNEL.sendToServer(new TimeSpeedPacket(currentSpeed, isEnabled));
                }
        ).bounds(centerX - 50, centerY + 20, 100, 20).build();
        this.addRenderableWidget(toggleButton);

        // Кнопка закрытия
        Button closeButton = Button.builder(
                Component.literal("Закрыть"),
                btn -> this.onClose()
        ).bounds(centerX - 50, centerY + 50, 100, 20).build();
        this.addRenderableWidget(closeButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Заголовок
        guiGraphics.drawCenteredString(this.font, this.title, centerX, 30, 0xFFFFFF);

        // Описание
        guiGraphics.drawCenteredString(this.font, "0.1 = очень медленно", centerX, 70, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, "1.0 = нормальная скорость", centerX, 80, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, "5.0 = в 5 раз быстрее", centerX, 90, 0xAAAAAA);
        guiGraphics.drawCenteredString(this.font, "10.0 = в 10 раз быстрее", centerX, 100, 0xAAAAAA);

        // Текущее значение (зелёным цветом)
        guiGraphics.drawCenteredString(this.font,
                String.format("Текущая скорость: %.1f x", currentSpeed),
                centerX, centerY - 60, 0x00FF00);

        // Статус
        String statusText = isEnabled ? "§a[АКТИВНО]" : "§c[ВЫКЛЮЧЕНО]";
        guiGraphics.drawCenteredString(this.font,
                statusText,
                centerX, centerY - 45, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Игра не ставится на паузу при открытии GUI
    }
}