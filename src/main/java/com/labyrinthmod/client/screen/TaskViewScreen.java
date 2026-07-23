package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.item.TaskItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class TaskViewScreen extends Screen {
    private final ItemStack taskStack;
    private final Runnable onTakeCallback;
    private Button takeButton;
    private Button backButton;

    private static final int SCREEN_WIDTH = 350;
    private static final int SCREEN_HEIGHT = 250;

    public TaskViewScreen(ItemStack taskStack, Runnable onTakeCallback) {
        super(Component.literal("Просмотр задания"));
        this.taskStack = taskStack;
        this.onTakeCallback = onTakeCallback;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        // Кнопка "Взять задание" (только если предмет не пустой)
        if (!taskStack.isEmpty()) {
            takeButton = Button.builder(
                    Component.literal("Взять задание"),
                    btn -> takeTask()
            ).bounds(x + 20, y + 200, 150, 20).build();
            this.addRenderableWidget(takeButton);
        }

        // Кнопка "Назад"
        backButton = Button.builder(
                Component.literal("Назад"),
                btn -> this.onClose()
        ).bounds(x + 180, y + 200, 150, 20).build();
        this.addRenderableWidget(backButton);
    }

    private void takeTask() {
        if (onTakeCallback != null) {
            onTakeCallback.run();
        }
        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        guiGraphics.fill(x, y, x + SCREEN_WIDTH, y + SCREEN_HEIGHT, 0xFF2d2d2d);
        guiGraphics.renderOutline(x, y, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFF555555);

        // Если предмет пустой - показываем заглушку
        if (taskStack.isEmpty()) {
            String emptyText = "Пустое задание";
            int textWidth = this.font.width(emptyText);
            guiGraphics.drawString(this.font, emptyText, x + (SCREEN_WIDTH - textWidth) / 2, y + 15, 0xFF888888, true);

            guiGraphics.hLine(x + 20, x + SCREEN_WIDTH - 20, y + 40, 0xFF555555);
            guiGraphics.drawString(this.font, "Здесь пока нет задания", x + 20, y + 55, 0xAAAAAA);
        } else {
            // Предмет есть - показываем данные задания
            TaskItem taskItem = (TaskItem) taskStack.getItem();

            String title = taskItem.getTitle(taskStack);
            int titleWidth = this.font.width(title);
            guiGraphics.drawString(this.font, title, x + (SCREEN_WIDTH - titleWidth) / 2, y + 15, 0xFFD700, true);

            guiGraphics.hLine(x + 20, x + SCREEN_WIDTH - 20, y + 40, 0xFFD700);

            String description = taskItem.getDescription(taskStack);
            String[] descLines = wrapText(description, 45);
            int descY = y + 55;
            for (String line : descLines) {
                guiGraphics.drawString(this.font, line, x + 20, descY, 0xFFFFFF);
                descY += 12;
            }

            int rewardY = descY + 15;
            guiGraphics.hLine(x + 20, x + SCREEN_WIDTH - 20, rewardY, 0xFFD700);

            String reward = taskItem.getReward(taskStack);
            guiGraphics.drawString(this.font, "Награда: " + reward, x + 20, rewardY + 10, 0x00FF00);

            String author = taskItem.getAuthor(taskStack);
            guiGraphics.drawString(this.font, "Автор: " + author, x + 20, rewardY + 25, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private String[] wrapText(String text, int maxCharsPerLine) {
        if (text == null || text.isEmpty()) {
            return new String[]{"Нет описания"};
        }

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
    public boolean isPauseScreen() {
        return false;
    }
}