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
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class TaskViewScreen extends Screen {
    private final ItemStack taskStack;
    private final Runnable onTakeCallback;
    private final Runnable onCompleteCallback;
    private final int taskSlotIndex;
    private Button takeButton;

    public TaskViewScreen(ItemStack taskStack) {
        this(taskStack, null, -1);
    }

    public TaskViewScreen(ItemStack taskStack, Runnable onCompleteCallback, int slotIndex) {
        super(Component.translatable("labyrinthmod.task.title"));
        this.taskStack = taskStack;
        this.onTakeCallback = null;
        this.onCompleteCallback = onCompleteCallback;
        this.taskSlotIndex = slotIndex;
    }

    public TaskViewScreen(ItemStack taskStack, Runnable onTakeCallback,
                          Runnable onCompleteCallback, int slotIndex) {
        super(Component.translatable("labyrinthmod.task.title"));
        this.taskStack = taskStack;
        this.onTakeCallback = onTakeCallback;
        this.onCompleteCallback = onCompleteCallback;
        this.taskSlotIndex = slotIndex;
    }

    @Override
    protected void init() {
        super.init();

        int paperW = 300;
        int paperH = 300;
        int x = (this.width - paperW) / 2;
        int y = (this.height - paperH) / 2;

        int buttonY = y + paperH - (onTakeCallback != null ? 95 : 65);

        if (onTakeCallback != null && taskSlotIndex >= 0) {
            this.addRenderableWidget(new PaperButton(
                    x + 30, buttonY, 240, 24,
                    Component.translatable("labyrinthmod.task.take_scroll"),
                    btn -> {
                        onTakeCallback.run();
                        this.onClose();
                    }
            ));
            buttonY += 30;
        }

        if (onCompleteCallback != null && taskSlotIndex >= 0) {
            takeButton = new PaperButton(
                    x + 30,
                    buttonY,
                    240,
                    24,
                    Component.translatable("labyrinthmod.task.complete"),
                    btn -> {
                        onCompleteCallback.run();
                        this.onClose();
                    }
            );
            this.addRenderableWidget(takeButton);
            buttonY += 30;
        }

        PaperButton closeButton = new PaperButton(
                x + 30,
                y + paperH - 35,
                240,
                24,
                Component.translatable("gui.close"),
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

        // Тень
        guiGraphics.fill(paperX - 4, paperY - 4, paperX + paperW + 4, paperY + paperH + 4, 0x80000000);
        // Бумага
        guiGraphics.fill(paperX, paperY, paperX + paperW, paperY + paperH, 0xFFE9DCB9);

        // Рамка
        int borderColor = 0xFF6B5842;
        guiGraphics.fill(paperX, paperY, paperX + paperW, paperY + 2, borderColor);
        guiGraphics.fill(paperX, paperY + paperH - 2, paperX + paperW, paperY + paperH, borderColor);
        guiGraphics.fill(paperX, paperY, paperX + 2, paperY + paperH, borderColor);
        guiGraphics.fill(paperX + paperW - 2, paperY, paperX + paperW, paperY + paperH, borderColor);

        String title = Component.translatable("labyrinthmod.quest.unnamed").getString();
        String description = Component.translatable("labyrinthmod.task.no_description").getString();
        String author = Component.translatable("labyrinthmod.quest.unknown_author").getString();
        boolean isCompleted = false;
        ListTag requiredItemsTag = null;

        if (taskStack.hasTag()) {
            CompoundTag tag = taskStack.getTag();
            if (!tag.getString("Title").isEmpty()) {
                title = tag.getString("Title");
            }
            if (!tag.getString("Description").isEmpty()) {
                description = tag.getString("Description");
            }
            if (!tag.getString("Author").isEmpty()) {
                author = tag.getString("Author");
            }
            isCompleted = tag.getBoolean("Completed");
            if (tag.contains("RequiredItems", Tag.TAG_LIST)) {
                requiredItemsTag = tag.getList("RequiredItems", Tag.TAG_COMPOUND);
            }
        }

        int padX = 20;
        int textY = paperY + 20;

        String displayTitle = isCompleted ? Component.translatable("labyrinthmod.task.title_completed", title).getString() : title;
        int titleWidth = this.font.width(displayTitle);
        int titleColor = isCompleted ? 0xFF6B5842 : 0xFF7A1F1F;
        guiGraphics.drawString(
                this.font,
                displayTitle,
                paperX + (paperW - titleWidth) / 2,
                textY,
                titleColor,
                false
        );
        textY += 16;
        guiGraphics.fill(paperX + padX, textY, paperX + paperW - padX, textY + 1, 0xFF7A1F1F);
        textY += 15;

        String[] descLines = wrapText(description, paperW - padX * 2);
        for (String line : descLines) {
            guiGraphics.drawString(this.font, line, paperX + padX, textY, 0xFF2A1810, false);
            textY += 12;
        }
        textY += 10;
        guiGraphics.fill(paperX + padX, textY, paperX + paperW - padX, textY + 1, 0xFF6B5842);
        textY += 15;

        guiGraphics.drawString(this.font, Component.translatable("labyrinthmod.task.author", author), paperX + padX, textY, 0xFF6B5842, false);

        if (requiredItemsTag != null && !requiredItemsTag.isEmpty()) {
            textY += 15;
            guiGraphics.drawString(this.font, Component.translatable("labyrinthmod.task.required_items"), paperX + padX, textY, 0xFF7A1F1F, false);
            textY += 12;
            for (int i = 0; i < requiredItemsTag.size(); i++) {
                CompoundTag itemTag = requiredItemsTag.getCompound(i);
                String itemId = getRequiredItemId(itemTag);
                int count = getRequiredItemCount(itemTag);

                ResourceLocation rl = itemId.isEmpty() ? null : ResourceLocation.tryParse(itemId);
                Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);

                if (item == null || item == Items.AIR) {
                    guiGraphics.drawString(
                            this.font,
                            "§cНеизвестный предмет: " + itemId,
                            paperX + padX,
                            textY + 6,
                            0xFF7A1F1F,
                            false
                    );
                    textY += 22;
                    continue;
                }

                ItemStack renderStack = new ItemStack(item, count);
                boolean hasItem = false;
                if (Minecraft.getInstance().player != null) {
                    hasItem = Minecraft.getInstance().player.getInventory().countItem(item) >= count;
                }

                int itemColor = hasItem ? 0xFF2A4A10 : 0xFF7A1F1F;

                guiGraphics.renderItem(renderStack, paperX + padX, textY);
                guiGraphics.renderItemDecorations(this.font, renderStack, paperX + padX, textY);

                String itemName = renderStack.getHoverName().getString();
                guiGraphics.drawString(
                        this.font,
                        itemName + " x" + count,
                        paperX + padX + 22,
                        textY + 6,
                        itemColor,
                        false
                );
                textY += 22;
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static String getRequiredItemId(CompoundTag tag) {
        String[] keys = new String[]{
                "ItemId",
                "itemId",
                "item",
                "id",
                "name"
        };
        for (String key : keys) {
            if (tag.contains(key)) {
                String value = tag.getString(key).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }

    private static int getRequiredItemCount(CompoundTag tag) {
        if (tag.contains("count")) {
            int c = tag.getInt("count");
            if (c > 0) return c;
        }
        if (tag.contains("Count")) {
            int c = tag.getInt("Count");
            if (c > 0) return c;
        }
        return 1;
    }

    private String[] wrapText(String text, int maxCharsPerLine) {
        if (text == null || text.isEmpty()) {
            return new String[]{Component.translatable("labyrinthmod.task.no_description").getString()};
        }
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
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
