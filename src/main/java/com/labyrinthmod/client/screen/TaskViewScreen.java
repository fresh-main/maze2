package com.labyrinthmod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TaskViewScreen extends Screen {
    private final ItemStack taskStack;
    private final Runnable onTakeCallback;
    private final int taskSlotIndex;
    private Button takeButton;
    private Button backButton;

    private static final int SCREEN_WIDTH = 350;
    private static final int SCREEN_HEIGHT = 250;

    public TaskViewScreen(ItemStack taskStack, Runnable onTakeCallback, int slotIndex) {
        super(Component.literal("Просмотр задания"));
        this.taskStack = taskStack;
        this.onTakeCallback = onTakeCallback;
        this.taskSlotIndex = slotIndex;
    }

    public TaskViewScreen(ItemStack taskStack) {
        this(taskStack, null, -1);
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        if (taskSlotIndex >= 0 && onTakeCallback != null) {
            takeButton = Button.builder(Component.literal("Принять задание"), btn -> {
                onTakeCallback.run();
                this.onClose();
            }).bounds(x + 20, y + 200, 150, 20).build();
            this.addRenderableWidget(takeButton);
        }

        int backX = (taskSlotIndex >= 0) ? x + 180 : x + 100;
        backButton = Button.builder(Component.literal("Назад"), btn -> this.onClose()).bounds(backX, y + 200, 150, 20).build();
        this.addRenderableWidget(backButton);
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
            guiGraphics.drawString(this.font, emptyText, x + (SCREEN_WIDTH - this.font.width(emptyText)) / 2, y + 15, 0xFF888888, true);
        } else {
            String title = "Без названия";
            String description = "Нет описания";
            String reward = "Нет награды";
            String author = "Неизвестно";
            boolean isCompleted = false;
            ListTag requiredItemsTag = null;

            if (taskStack.hasTag()) {
                CompoundTag tag = taskStack.getTag();
                if (!tag.getString("Title").isEmpty()) title = tag.getString("Title");
                if (!tag.getString("Description").isEmpty()) description = tag.getString("Description");
                if (!tag.getString("Reward").isEmpty()) reward = tag.getString("Reward");
                if (!tag.getString("Author").isEmpty()) author = tag.getString("Author");
                isCompleted = tag.getBoolean("Completed");

                if (tag.contains("RequiredItems", Tag.TAG_LIST)) {
                    requiredItemsTag = tag.getList("RequiredItems", Tag.TAG_COMPOUND);
                }
            }

            String displayTitle = title + (isCompleted ? " §7(выполнено)" : "");
            int titleWidth = this.font.width(displayTitle);
            int titleColor = isCompleted ? 0xFF888888 : 0xFFD700;
            guiGraphics.drawString(this.font, displayTitle, x + (SCREEN_WIDTH - titleWidth) / 2, y + 15, titleColor, true);
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

            // Отрисовка списка требуемых предметов
            if (requiredItemsTag != null && !requiredItemsTag.isEmpty()) {
                int itemY = rewardY + 40;
                guiGraphics.drawString(this.font, "Нужно принести:", x + 20, itemY, 0xFFAA00);
                itemY += 12;

                for (int i = 0; i < requiredItemsTag.size() && i < 5; i++) {
                    CompoundTag itemTag = requiredItemsTag.getCompound(i);
                    String itemId = itemTag.getString("ItemId");
                    int count = itemTag.getInt("Count");
                    if (!itemId.isEmpty()) {
                        guiGraphics.drawString(this.font, "  " + count + "x " + itemId, x + 20, itemY, 0xFFCC00);
                        itemY += 10;
                    }
                }
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private String[] wrapText(String text, int maxCharsPerLine) {
        if (text == null || text.isEmpty()) return new String[]{"Нет описания"};
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
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