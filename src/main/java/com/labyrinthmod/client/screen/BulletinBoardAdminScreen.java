package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.AddTaskPacket;
import com.labyrinthmod.common.network.packet.RemoveTaskPacket;
import com.labyrinthmod.common.network.packet.RequestTimerUpdatePacket;
import com.labyrinthmod.common.network.packet.ResetTimerPacket;
import com.labyrinthmod.common.network.packet.SetSpawnIntervalPacket;
import com.labyrinthmod.common.network.packet.SpawnSpecificTaskPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BulletinBoardAdminScreen extends Screen {
    private final BulletinBoardBlockEntity blockEntity;

    private EditBox intervalInput;
    private Button saveButton;

    private EditBox titleInput;
    private EditBox descriptionInput;
    private EditBox rewardInput;
    private EditBox authorInput;
    private Button addTaskButton;

    private Button removeTaskButton;
    private Button spawnSelectedButton;
    private Button backButton;

    private static final int SCREEN_WIDTH = 450;
    private static final int SCREEN_HEIGHT = 350;
    private int selectedTaskIndex = -1;
    private int displaySecondsLeft = 0;

    public BulletinBoardAdminScreen(BulletinBoardBlockEntity blockEntity) {
        super(Component.literal("Настройка доски объявлений"));
        this.blockEntity = blockEntity;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        // ========== ЛЕВАЯ ЧАСТЬ ==========

        // Поле интервала
        intervalInput = new EditBox(this.font, x + 10, y + 30, 70, 20, Component.literal("Интервал"));
        intervalInput.setValue(String.valueOf(blockEntity.getSpawnIntervalSeconds()));
        intervalInput.setMaxLength(10);
        this.addWidget(intervalInput);

        saveButton = Button.builder(
                Component.literal("Сохранить"),
                btn -> {
                    try {
                        int seconds = Integer.parseInt(intervalInput.getValue());
                        NetworkHandler.CHANNEL.sendToServer(new SetSpawnIntervalPacket(blockEntity.getBlockPos(), seconds));
                    } catch (NumberFormatException e) {}
                }
        ).bounds(x + 85, y + 30, 65, 20).build();
        this.addRenderableWidget(saveButton);

        Button refreshTimerButton = Button.builder(
                Component.literal("🔄"),
                btn -> NetworkHandler.CHANNEL.sendToServer(new RequestTimerUpdatePacket(blockEntity.getBlockPos()))
        ).bounds(x + 155, y + 30, 45, 20).build();
        this.addRenderableWidget(refreshTimerButton);

        Button resetTimerButton = Button.builder(
                Component.literal("Сбросить таймер"),
                btn -> NetworkHandler.CHANNEL.sendToServer(new ResetTimerPacket(blockEntity.getBlockPos()))
        ).bounds(x + 10, y + 60, 190, 20).build();
        this.addRenderableWidget(resetTimerButton);

        // Поля создания задания
        int createY = y + 100;

        titleInput = new EditBox(this.font, x + 10, createY, 190, 20, Component.literal("Название"));
        titleInput.setHint(Component.literal("Название задания"));
        this.addWidget(titleInput);
        createY += 25;

        descriptionInput = new EditBox(this.font, x + 10, createY, 190, 20, Component.literal("Описание"));
        descriptionInput.setHint(Component.literal("Описание"));
        this.addWidget(descriptionInput);
        createY += 25;

        rewardInput = new EditBox(this.font, x + 10, createY, 190, 20, Component.literal("Награда"));
        rewardInput.setHint(Component.literal("Награда"));
        this.addWidget(rewardInput);
        createY += 25;

        authorInput = new EditBox(this.font, x + 10, createY, 190, 20, Component.literal("Автор"));
        authorInput.setHint(Component.literal("Автор"));
        this.addWidget(authorInput);
        createY += 30;

        addTaskButton = Button.builder(
                Component.literal("Добавить задание"),
                btn -> addTask()
        ).bounds(x + 10, createY, 190, 20).build();
        this.addRenderableWidget(addTaskButton);

        // ========== ПРАВАЯ ЧАСТЬ ==========
        int rightX = x + 210;

        removeTaskButton = Button.builder(
                Component.literal("Удалить выбранное"),
                btn -> removeTask()
        ).bounds(rightX, y + 25, 220, 20).build();
        this.addRenderableWidget(removeTaskButton);

        spawnSelectedButton = Button.builder(
                Component.literal("Спавн выбранное"),
                btn -> spawnSelectedTask()
        ).bounds(rightX, y + 50, 220, 20).build();
        this.addRenderableWidget(spawnSelectedButton);

        backButton = Button.builder(
                Component.literal("Назад"),
                btn -> this.onClose()
        ).bounds(rightX, y + SCREEN_HEIGHT - 45, 220, 20).build();
        this.addRenderableWidget(backButton);
    }

    @Override
    public void tick() {
        int ticksLeft = blockEntity.getTicksPerSpawn() - blockEntity.getSpawnTimer();
        displaySecondsLeft = Math.max(0, ticksLeft / 20);
    }

    private void addTask() {
        String title = titleInput.getValue().isEmpty() ? "Новое задание" : titleInput.getValue();
        String description = descriptionInput.getValue().isEmpty() ? "Описание задания" : descriptionInput.getValue();
        String reward = rewardInput.getValue().isEmpty() ? "100 монет" : rewardInput.getValue();
        String author = authorInput.getValue().isEmpty() ? "Админ" : authorInput.getValue();

        CompoundTag taskData = new CompoundTag();
        taskData.putString("id", "labyrinthmod:task_item");
        taskData.putInt("Count", 1);
        CompoundTag tag = new CompoundTag();
        tag.putString("Title", title);
        tag.putString("Description", description);
        tag.putString("Reward", reward);
        tag.putString("Author", author);
        taskData.put("tag", tag);

        NetworkHandler.CHANNEL.sendToServer(new AddTaskPacket(blockEntity.getBlockPos(), taskData));

        titleInput.setValue("");
        descriptionInput.setValue("");
        rewardInput.setValue("");
        authorInput.setValue("");
    }

    private void removeTask() {
        if (selectedTaskIndex >= 0) {
            NetworkHandler.CHANNEL.sendToServer(new RemoveTaskPacket(blockEntity.getBlockPos(), selectedTaskIndex));
            selectedTaskIndex = -1;
        }
    }

    private void spawnSelectedTask() {
        if (selectedTaskIndex >= 0) {
            NetworkHandler.CHANNEL.sendToServer(new SpawnSpecificTaskPacket(blockEntity.getBlockPos(), selectedTaskIndex));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        // Фон
        guiGraphics.fill(x, y, x + SCREEN_WIDTH, y + SCREEN_HEIGHT, 0xFF2d2d2d);
        guiGraphics.renderOutline(x, y, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFF555555);

        // Заголовок
        String title = "Настройка доски объявлений";
        guiGraphics.drawString(this.font, title, x + (SCREEN_WIDTH - this.font.width(title)) / 2, y + 10, 0xFFD700, true);

        // ===== ПОДПИСИ СЛЕВА =====
        guiGraphics.drawString(this.font, "Интервал (сек):", x + 10, y + 15, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Создание задания:", x + 10, y + 85, 0xFFD700);

        // ===== ПОДПИСЬ СПРАВА =====
        guiGraphics.drawString(this.font, "Предзагруженные задания:", x + 210, y + 10, 0xFFD700);

        // ===== СПИСОК ЗАДАНИЙ =====
        List<CompoundTag> preloaded = blockEntity.getPreloadedTasks();
        int listX = x + 210;
        int listY = y + 80; // Список начинается ПОСЛЕ кнопок
        int listHeight = SCREEN_HEIGHT - 130;

        // Фон списка
        guiGraphics.fill(listX - 5, listY - 5, listX + 240, listY + listHeight, 0xFF1a1a1a);
        guiGraphics.renderOutline(listX - 5, listY - 5, 245, listHeight + 10, 0xFF444444);

        if (preloaded.isEmpty()) {
            guiGraphics.drawString(this.font, "§7Нет заданий в очереди", listX + 10, listY + 10, 0x888888);
        } else {
            for (int i = 0; i < preloaded.size() && i < 8; i++) {
                CompoundTag taskData = preloaded.get(i);
                String taskTitle = taskData.getCompound("tag").getString("Title");
                String taskAuthor = taskData.getCompound("tag").getString("Author");

                int color = (i == selectedTaskIndex) ? 0xFFFF00 : 0xAAAAAA;
                boolean hover = mouseX >= listX - 5 && mouseX < listX + 240 &&
                        mouseY >= listY + i * 26 && mouseY < listY + i * 26 + 24;
                if (hover) color = 0xFFFFFF;

                guiGraphics.drawString(this.font, (i + 1) + ". " + taskTitle, listX, listY + i * 26, color);
                guiGraphics.drawString(this.font, "§8Автор: " + taskAuthor, listX, listY + i * 26 + 12, 0x888888);
            }
        }

        // ===== ПОЛЯ ВВОДА =====
        intervalInput.render(guiGraphics, mouseX, mouseY, partialTick);
        titleInput.render(guiGraphics, mouseX, mouseY, partialTick);
        descriptionInput.render(guiGraphics, mouseX, mouseY, partialTick);
        rewardInput.render(guiGraphics, mouseX, mouseY, partialTick);
        authorInput.render(guiGraphics, mouseX, mouseY, partialTick);

        // ===== ТАЙМЕР ВНИЗУ СЛЕВА =====
        guiGraphics.drawString(this.font, "До следующего задания: " + displaySecondsLeft + " сек",
                x + 10, y + SCREEN_HEIGHT - 50, 0x00FF00);
        guiGraphics.drawString(this.font, "Очередь: " + preloaded.size() + " заданий",
                x + 10, y + SCREEN_HEIGHT - 35, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (this.width - SCREEN_WIDTH) / 2;
            int y = (this.height - SCREEN_HEIGHT) / 2;
            int listX = x + 210;
            int listY = y + 80;
            List<CompoundTag> preloaded = blockEntity.getPreloadedTasks();

            for (int i = 0; i < preloaded.size() && i < 8; i++) {
                if (mouseX >= listX - 5 && mouseX < listX + 240 &&
                        mouseY >= listY + i * 26 && mouseY < listY + i * 26 + 24) {
                    selectedTaskIndex = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}