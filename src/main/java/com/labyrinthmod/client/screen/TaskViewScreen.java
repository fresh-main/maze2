package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.item.TaskItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class TaskViewScreen extends Screen {
    private final ItemStack taskStack;
    private final TaskItem taskItem;
    private Button takeButton;
    private Button backButton;
    private final Runnable onTakeCallback;

    public TaskViewScreen(ItemStack taskStack, Runnable onTakeCallback) {
        super(Component.translatable("gui.labyrinthmod.task_view"));
        this.taskStack = taskStack;
        this.taskItem = (TaskItem) taskStack.getItem();
        this.onTakeCallback = onTakeCallback;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - 300) / 2;
        int y = (this.height - 250) / 2;

        // Кнопка взять задание
        takeButton = Button.builder(Component.translatable("gui.labyrinthmod.take_task"), btn -> takeTask())
                .bounds(x + 10, y + 210, 135, 20)
                .build();
        this.addWidget(takeButton);

        // Кнопка назад
        backButton = Button.builder(Component.translatable("gui.labyrinthmod.back"), btn -> this.onClose())
                .bounds(x + 155, y + 210, 135, 20)
                .build();
        this.addWidget(backButton);
    }

    private void takeTask() {
        onTakeCallback.run();
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Темный фон
        this.renderBackground(guiGraphics);

        int x = (this.width - 300) / 2;
        int y = (this.height - 250) / 2;

        // Фон для текста
        guiGraphics.fill(x, y, x + 300, y + 250, 0xDD1a1a1a);
        guiGraphics.fill(x + 2, y + 2, x + 298, y + 248, 0xFF2d2d2d);

        // Заголовок
        String title = taskItem.getTitle(taskStack);
        guiGraphics.drawString(this.font, title, x + 150 - this.font.width(title) / 2, y + 15, 0xFFD700, true);

        // Разделитель
        guiGraphics.hLine(x + 10, x + 290, y + 35, 0xFFD700);

        // Описание
        String description = taskItem.getDescription(taskStack);
        String[] descLines = wrapText(description, 50);
        int descY = y + 50;
        for (String line : descLines) {
            guiGraphics.drawString(this.font, line, x + 15, descY, 0xFFFFFF);
            descY += 12;
        }

        // Разделитель
        int rewardY = descY + 10;
        guiGraphics.hLine(x + 10, x + 290, rewardY, 0xFFD700);

        // Награда
        String reward = taskItem.getReward(taskStack);
        String rewardText = "Награда: " + reward;
        guiGraphics.drawString(this.font, rewardText, x + 15, rewardY + 10, 0x00FF00);

        // Автор
        String author = taskItem.getAuthor(taskStack);
        String authorText = "Автор: " + author;
        guiGraphics.drawString(this.font, authorText, x + 15, rewardY + 25, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private String[] wrapText(String text, int maxCharsPerLine) {
        String[] words = text.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 <= maxCharsPerLine) {
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}