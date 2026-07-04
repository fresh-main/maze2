package com.otbor.client;

import com.labyrinthmod.LabyrinthMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class LabyrinthVerificationScreen extends Screen {
    private final Screen parent;
    private boolean fileExists = false;
    private String statusMessage = "Проверка ресурсов...";

    public LabyrinthVerificationScreen(Screen parent) {
        super(Component.literal("ДИАГНОСТИКА ЛАБИРИНТА"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        checkResources();

        int buttonWidth = 260;
        int buttonHeight = 20;
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Кнопка "Назад"
        this.addRenderableWidget(Button.builder(Component.literal("← Назад в меню выбора мира"), b -> {
            this.minecraft.setScreen(parent);
        }).bounds(centerX - buttonWidth / 2, centerY + 80, buttonWidth, buttonHeight).build());

        // Кнопка "Открыть стандартное создание мира"
        this.addRenderableWidget(Button.builder(Component.literal("Открыть стандартное меню создания мира"), b -> {
            CreateWorldScreen.openFresh(this.minecraft, this);
        }).bounds(centerX - buttonWidth / 2, centerY + 110, buttonWidth, buttonHeight).build());
    }

    private void checkResources() {
        if (this.minecraft != null) {
            try {
                // Пытаемся найти наш сгенерированный JSON-файл в ресурсах
                ResourceLocation presetPath = ResourceLocation.fromNamespaceAndPath(LabyrinthMod.MOD_ID, "worldgen/world_preset/labyrinth.json");
                var resource = this.minecraft.getResourceManager().getResource(presetPath);

                if (resource.isPresent()) {
                    this.fileExists = true;
                    this.statusMessage = "✅ УСПЕХ: Файл 'labyrinth.json' НАЙДЕН в ресурсах игры!\n\n" +
                            "Это означает, что DataGen сработал, и build.gradle настроен верно.\n" +
                            "Генератор ТОЧНО зарегистрирован.\n\n" +
                            "Если вы не видите его в списке, проблема на 100% в одном из двух:\n" +
                            "1. Вы ищете не то название. Ищите в списке строчку:\n   'worldType.labyrinthmod.labyrinth'\n" +
                            "2. Вы смотрите не на той вкладке. При создании мира нужно перейти на вкладку 'Мир' (World).";
                } else {
                    this.fileExists = false;
                    this.statusMessage = "❌ ОШИБКА: Файл 'labyrinth.json' НЕ НАЙДЕН.\n\n" +
                            "Игра не видит сгенерированный файл. Убедитесь, что вы:\n" +
                            "1. Удалили ручной (сломанный) файл из src/main/resources/data/...\n" +
                            "2. Запустили задачу Gradle 'runData'\n" +
                            "3. Выполнили 'gradlew clean' перед запуском клиента.";
                }
            } catch (Exception e) {
                this.fileExists = false;
                this.statusMessage = "❌ ОШИБКА при проверке: " + e.getMessage();
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        Font font = this.font;
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        gfx.drawCenteredString(font, this.title, centerX, centerY - 90, 0xFFFFFF);

        // Разбиваем сообщение на строки для удобного чтения
        String[] lines = this.statusMessage.split("\n");
        int yOffset = centerY - 60;
        for (String line : lines) {
            int color = 0xFFFFFF;
            if (line.startsWith("✅")) color = 0x55FF55; // Зеленый
            else if (line.startsWith("❌")) color = 0xFF5555; // Красный
            else if (line.contains("1.") || line.contains("2.") || line.contains("3.")) color = 0xFFFF55; // Желтый для списков
            else if (line.contains("worldType.")) color = 0x55FFFF; // Голубой для технического ключа

            gfx.drawCenteredString(font, line, centerX, yOffset, color);
            yOffset += 12;
        }

        super.render(gfx, mouseX, mouseY, partialTick);
    }
}