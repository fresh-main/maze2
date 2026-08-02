package com.labyrinthmod.client.screen;

import com.otbor.client.widgets.PaperButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

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
        int paperW = 300;
        int paperH = 300;
        int x = (this.width - paperW) / 2;
        int y = (this.height - paperH) / 2;

        // Кнопка "Принять задание" (используем PaperButton)
        if (onTakeCallback != null && taskSlotIndex >= 0) {
            PaperButton takeButton = new PaperButton(
                    x + 30, y + paperH - 65, 240, 24,
                    Component.literal("Принять задание"),
                    btn -> {
                        onTakeCallback.run();
                        this.onClose();
                    }
            );
            this.addRenderableWidget(takeButton);
        }

        // Кнопка "Закрыть" (используем PaperButton)
        PaperButton closeButton = new PaperButton(
                x + 30, y + paperH - 35, 240, 24,
                Component.literal("Закрыть"),
                btn -> this.onClose()
        );
        this.addRenderableWidget(closeButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int paperW = 300;
        int paperH = 300;
        int paperX = (this.width - paperW) / 2;
        int paperY = (this.height - paperH) / 2;

        // 1. Тень бумаги
        guiGraphics.fill(paperX - 4, paperY - 4, paperX + paperW + 4, paperY + paperH + 4, 0x80000000);
        // 2. Основная бумага (бежевый цвет)
        guiGraphics.fill(paperX, paperY, paperX + paperW, paperY + paperH, 0xFFE9DCB9);

        // 3. Окантовка бумаги (темно-коричневая)
        int borderColor = 0xFF6B5842;
        guiGraphics.fill(paperX, paperY, paperX + paperW, paperY + 2, borderColor); // верх
        guiGraphics.fill(paperX, paperY + paperH - 2, paperX + paperW, paperY + paperH, borderColor); // низ
        guiGraphics.fill(paperX, paperY, paperX + 2, paperY + paperH, borderColor); // лево
        guiGraphics.fill(paperX + paperW - 2, paperY, paperX + paperW, paperY + paperH, borderColor); // право

        // Чтение данных из NBT
        String title = "Без названия";
        String description = "Нет описания";
        String author = "Неизвестно";
        int xpReward = 0;
        boolean isCompleted = false;
        ListTag requiredItemsTag = null;

        if (taskStack.hasTag()) {
            CompoundTag tag = taskStack.getTag();
            if (!tag.getString("Title").isEmpty()) title = tag.getString("Title");
            if (!tag.getString("Description").isEmpty()) description = tag.getString("Description");
            if (!tag.getString("Author").isEmpty()) author = tag.getString("Author");

            xpReward = tag.getInt("XpReward");
            isCompleted = tag.getBoolean("Completed");

            if (tag.contains("RequiredItems", Tag.TAG_LIST)) {
                requiredItemsTag = tag.getList("RequiredItems", Tag.TAG_COMPOUND);
            }
        }

        int padX = 20;
        int textY = paperY + 20;

        // Заголовок
        String displayTitle = title + (isCompleted ? " §7(выполнено)" : "");
        int titleWidth = this.font.width(displayTitle);
        int titleColor = isCompleted ? 0xFF6B5842 : 0xFF7A1F1F; // Красный акцент или выцветший
        guiGraphics.drawString(this.font, displayTitle, paperX + (paperW - titleWidth) / 2, textY, titleColor, false);

        // Линия-разделитель под заголовком
        textY += 16;
        guiGraphics.fill(paperX + padX, textY, paperX + paperW - padX, textY + 1, 0xFF7A1F1F);
        textY += 15;

        // Описание задания
        String[] descLines = wrapText(description, paperW - padX * 2);
        for (String line : descLines) {
            guiGraphics.drawString(this.font, line, paperX + padX, textY, 0xFF2A1810, false);
            textY += 12;
        }

        textY += 10;
        guiGraphics.fill(paperX + padX, textY, paperX + paperW - padX, textY + 1, 0xFF6B5842);
        textY += 15;

        // Награда и автор
        guiGraphics.drawString(this.font, "Награда: " + xpReward + " ур. опыта", paperX + padX, textY, 0xFF2A1810, false);
        textY += 12;
        guiGraphics.drawString(this.font, "Автор: " + author, paperX + padX, textY, 0xFF6B5842, false);

        // Требуемые предметы
        if (requiredItemsTag != null && !requiredItemsTag.isEmpty()) {
            textY += 15;
            guiGraphics.drawString(this.font, "Требуемые предметы:", paperX + padX, textY, 0xFF7A1F1F, false);
            textY += 12;

            for (int i = 0; i < requiredItemsTag.size(); i++) {
                CompoundTag itemTag = requiredItemsTag.getCompound(i);
                String itemId = itemTag.getString("ItemId");
                int count = itemTag.getInt("Count");

                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
                if (item != null) {
                    ItemStack renderStack = new ItemStack(item, count);

                    boolean hasItem = false;
                    if (Minecraft.getInstance().player != null) {
                        hasItem = Minecraft.getInstance().player.getInventory().countItem(item) >= count;
                    }

                    // Зеленый, если предмет есть, красный, если не хватает
                    int itemColor = hasItem ? 0xFF2A4A10 : 0xFF7A1F1F;

                    guiGraphics.renderItem(renderStack, paperX + padX, textY);
                    guiGraphics.renderItemDecorations(this.font, renderStack, paperX + padX, textY);

                    String itemName = renderStack.getHoverName().getString();
                    guiGraphics.drawString(this.font, itemName + " x" + count, paperX + padX + 22, textY + 6, itemColor, false);

                    textY += 22;
                }
            }
        }

        // Рендерим кнопки поверх бумаги
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