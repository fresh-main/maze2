package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.item.TaskScrollItem;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.TakeTaskPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class TaskViewScreen extends Screen {
    private final ItemStack taskStack;
    private final Runnable onTakeCallback;
    private Button takeButton;
    private Button backButton;
    private final int taskSlotIndex; // Индекс слота на доске (-1 если из свитка)

    private static final int SCREEN_WIDTH = 350;
    private static final int SCREEN_HEIGHT = 250;

    // Конструктор для задания с доски
    public TaskViewScreen(ItemStack taskStack, Runnable onTakeCallback, int slotIndex) {
        super(Component.literal("Просмотр задания"));
        this.taskStack = taskStack;
        this.onTakeCallback = onTakeCallback;
        this.taskSlotIndex = slotIndex;
    }

    // Конструктор для свитка (без кнопки принять)
    public TaskViewScreen(ItemStack taskStack) {
        this(taskStack, null, -1);
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        // Кнопка "Принять задание" (только если задание с доски)
        if (taskSlotIndex >= 0 && onTakeCallback != null) {
            takeButton = Button.builder(
                    Component.literal("Принять задание"),
                    btn -> takeTask()
            ).bounds(x + 20, y + 200, 150, 20).build();
            this.addRenderableWidget(takeButton);
        }

        // Кнопка "Назад"
        backButton = Button.builder(
                Component.literal("Назад"),
                btn -> this.onClose()
        ).bounds(x + (taskSlotIndex >= 0 ? 180 : 100), y + 200, 150, 20).build();
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

        if (taskStack.isEmpty()) {
            String emptyText = "Пустое задание";
            int textWidth = this.font.width(emptyText);
            guiGraphics.drawString(this.font, emptyText, x + (SCREEN_WIDTH - textWidth) / 2, y + 15, 0xFF888888, true);
            guiGraphics.hLine(x + 20, x + SCREEN_WIDTH - 20, y + 40, 0xFF555555);
            guiGraphics.drawString(this.font, "Здесь пока нет задания", x + 20, y + 55, 0xAAAAAA);
        } else {
            String title = "";
            String description = "";
            String reward = "";
            String author = "";

            if (taskStack.hasTag()) {
                CompoundTag tag = taskStack.getTag();
                title = tag.getString("Title");
                description = tag.getString("Description");
                reward = tag.getString("Reward");
                author = tag.getString("Author");
            }

            if (title.isEmpty()) title = "Без названия";
            if (description.isEmpty()) description = "Нет описания";
            if (reward.isEmpty()) reward = "Нет награды";
            if (author.isEmpty()) author = "Неизвестно";

            int titleWidth = this.font.width(title);
            guiGraphics.drawString(this.font, title, x + (SCREEN_WIDTH - titleWidth) / 2, y + 15, 0xFFD700, true);
            guiGraphics.hLine(x + 20, x + SCREEN_WIDTH - 20, y + 40, 0xFFD700);

            String[] descLines = wrapText(description, 45);
            int descY = y + 55;
            for (String line : descLines) {
                guiGraphics.drawString(this.font, line, x + 20, descY, 0xFFFFFF);
                descY += 12;
            }

            int rewardY = descY + 15;
            guiGraphics.hLine(x + 20, x + SCREEN_WIDTH - 20, rewardY, 0xFFD700);
            guiGraphics.drawString(this.font, "Награда: " + reward, x + 20, rewardY + 10, 0x00FF00);
            guiGraphics.drawString(this.font, "Автор: " + author, x + 20, rewardY + 25, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private String[] wrapText(String text, int maxCharsPerLine) {
        if (text == null || text.isEmpty()) return new String[]{"Нет описания"};
        String[] words = text.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            if (currentLine.length() + word.length() + 1 <= maxCharsPerLine) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines.toArray(new String[0]);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}