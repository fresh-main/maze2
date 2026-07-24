package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BulletinBoardAdminScreen extends Screen {
    private final BulletinBoardBlockEntity blockEntity;

    // Поля для интервала
    private EditBox intervalInput;
    private Button saveButton;

    // Поля для создания задания
    private EditBox titleInput;
    private EditBox descriptionInput;
    private EditBox rewardInput;
    private EditBox authorInput;
    private Button addTaskButton;

    // Кнопки списка
    private Button removeTaskButton;
    private Button backButton;

    private static final int SCREEN_WIDTH = 450;
    private static final int SCREEN_HEIGHT = 350;
    private int selectedTaskIndex = -1;

    public BulletinBoardAdminScreen(BulletinBoardBlockEntity blockEntity) {
        super(Component.literal("Настройка доски объявлений"));
        this.blockEntity = blockEntity;
    }

    @Override
    protected void init() {
        super.init();

        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        // ========== ЛЕВАЯ ЧАСТЬ: Интервал и создание задания ==========

        // Поле ввода интервала
        intervalInput = new EditBox(this.font, x + 10, y + 30, 100, 20, Component.literal("Интервал"));
        intervalInput.setValue(String.valueOf(blockEntity.getSpawnIntervalSeconds()));
        intervalInput.setMaxLength(10);
        this.addWidget(intervalInput);

        // Кнопка сохранить интервал
        saveButton = Button.builder(
                Component.literal("Сохранить"),
                btn -> {
                    try {
                        int seconds = Integer.parseInt(intervalInput.getValue());
                        blockEntity.setSpawnIntervalSeconds(seconds);
                    } catch (NumberFormatException e) {}
                }
        ).bounds(x + 115, y + 30, 80, 20).build();
        this.addRenderableWidget(saveButton);

        // Поля для создания задания
        int createY = y + 65;

        titleInput = new EditBox(this.font, x + 10, createY, 185, 20, Component.literal("Название"));
        titleInput.setHint(Component.literal("Название задания"));
        this.addWidget(titleInput);
        createY += 25;

        descriptionInput = new EditBox(this.font, x + 10, createY, 185, 20, Component.literal("Описание"));
        descriptionInput.setHint(Component.literal("Описание"));
        this.addWidget(descriptionInput);
        createY += 25;

        rewardInput = new EditBox(this.font, x + 10, createY, 185, 20, Component.literal("Награда"));
        rewardInput.setHint(Component.literal("Награда"));
        this.addWidget(rewardInput);
        createY += 25;

        authorInput = new EditBox(this.font, x + 10, createY, 185, 20, Component.literal("Автор"));
        authorInput.setHint(Component.literal("Автор"));
        this.addWidget(authorInput);
        createY += 30;

        // Кнопка добавить
        addTaskButton = Button.builder(
                Component.literal("Добавить задание"),
                btn -> addTask()
        ).bounds(x + 10, createY, 185, 20).build();
        this.addRenderableWidget(addTaskButton);

        // ========== ПРАВАЯ ЧАСТЬ: Список заданий ==========

        int rightX = x + 210;

        // Кнопка удалить
        removeTaskButton = Button.builder(
                Component.literal("Удалить выбранное"),
                btn -> removeTask()
        ).bounds(rightX, y + 30, 220, 20).build();
        this.addRenderableWidget(removeTaskButton);

        // Кнопка назад
        backButton = Button.builder(
                Component.literal("Назад"),
                btn -> this.onClose()
        ).bounds(rightX, y + SCREEN_HEIGHT - 30, 220, 20).build();
        this.addRenderableWidget(backButton);
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

        blockEntity.addPreloadedTask(taskData);

        // Очистить поля
        titleInput.setValue("");
        descriptionInput.setValue("");
        rewardInput.setValue("");
        authorInput.setValue("");
    }

    private void removeTask() {
        if (selectedTaskIndex >= 0) {
            blockEntity.removePreloadedTask(selectedTaskIndex);
            selectedTaskIndex = -1;
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

        // Подписи слева
        guiGraphics.drawString(this.font, "Интервал (сек):", x + 10, y + 15, 0xFFFFFF);
        guiGraphics.drawString(this.font, "Создание задания:", x + 10, y + 55, 0xFFD700);

        // Подпись справа
        guiGraphics.drawString(this.font, "Предзагруженные задания:", x + 210, y + 15, 0xFFD700);

        // Отображение списка заданий
        List<CompoundTag> preloaded = blockEntity.getPreloadedTasks();
        int listX = x + 210;
        int listY = y + 60;
        int listHeight = SCREEN_HEIGHT - 100;

        // Фон списка
        guiGraphics.fill(listX - 5, listY - 5, listX + 240, listY + listHeight, 0xFF1a1a1a);
        guiGraphics.renderOutline(listX - 5, listY - 5, 245, listHeight + 10, 0xFF444444);

        for (int i = 0; i < preloaded.size() && i < 8; i++) {
            CompoundTag taskData = preloaded.get(i);
            String taskTitle = taskData.getCompound("tag").getString("Title");
            String taskAuthor = taskData.getCompound("tag").getString("Author");

            int color = (i == selectedTaskIndex) ? 0xFFFF00 : 0xAAAAAA;
            boolean hover = mouseX >= listX - 5 && mouseX < listX + 240 &&
                    mouseY >= listY + i * 25 && mouseY < listY + i * 25 + 23;
            if (hover) color = 0xFFFFFF;

            guiGraphics.drawString(this.font, (i + 1) + ". " + taskTitle, listX, listY + i * 25, color);
            guiGraphics.drawString(this.font, "Автор: " + taskAuthor, listX, listY + i * 25 + 12, 0x888888);
        }

        // Поля ввода
        intervalInput.render(guiGraphics, mouseX, mouseY, partialTick);
        titleInput.render(guiGraphics, mouseX, mouseY, partialTick);
        descriptionInput.render(guiGraphics, mouseX, mouseY, partialTick);
        rewardInput.render(guiGraphics, mouseX, mouseY, partialTick);
        authorInput.render(guiGraphics, mouseX, mouseY, partialTick);

        // Отображение таймера
        int ticksLeft = blockEntity.getTicksPerSpawn() - blockEntity.getSpawnTimer();
        int secondsLeft = ticksLeft / 20;
        guiGraphics.drawString(this.font, "До следующего задания: " + secondsLeft + " сек",
                x + 10, y + SCREEN_HEIGHT - 50, 0x00FF00);
        guiGraphics.drawString(this.font, "Очередь: " + blockEntity.getPreloadedTasksCount() + " заданий",
                x + 10, y + SCREEN_HEIGHT - 35, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Клик по списку заданий
        if (button == 0) {
            int x = (this.width - SCREEN_WIDTH) / 2;
            int y = (this.height - SCREEN_HEIGHT) / 2;
            int listX = x + 210;
            int listY = y + 60;
            List<CompoundTag> preloaded = blockEntity.getPreloadedTasks();

            for (int i = 0; i < preloaded.size() && i < 8; i++) {
                if (mouseX >= listX - 5 && mouseX < listX + 240 &&
                        mouseY >= listY + i * 25 && mouseY < listY + i * 25 + 23) {
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