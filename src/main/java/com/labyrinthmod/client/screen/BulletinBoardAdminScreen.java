package com.labyrinthmod.client.screen;

import com.labyrinthmod.common.blockentity.BulletinBoardBlockEntity;
import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.SaveQuestToJsonPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class BulletinBoardAdminScreen extends Screen {
    private final BulletinBoardBlockEntity blockEntity;

    private EditBox titleInput;
    private EditBox descriptionInput;
    private EditBox authorInput;

    private EditBox requiredItemInput;
    private EditBox requiredCountInput;
    private List<SaveQuestToJsonPacket.RequiredItemData> requiredItems = new ArrayList<>();

    private static final int SCREEN_WIDTH = 400;
    private static final int SCREEN_HEIGHT = 300;

    public BulletinBoardAdminScreen(BulletinBoardBlockEntity blockEntity) {
        super(Component.translatable("labyrinthmod.quest_admin.title"));
        this.blockEntity = blockEntity;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        int currentY = y + 20;

        titleInput = new EditBox(this.font, x + 10, currentY, 380, 20, Component.translatable("labyrinthmod.quest_admin.name"));
        titleInput.setHint(Component.translatable("labyrinthmod.quest_admin.name_hint"));
        this.addWidget(titleInput);
        currentY += 25;

        descriptionInput = new EditBox(this.font, x + 10, currentY, 380, 20, Component.translatable("labyrinthmod.quest_admin.description"));
        descriptionInput.setHint(Component.translatable("labyrinthmod.quest_admin.description_hint"));
        this.addWidget(descriptionInput);
        currentY += 25;

        authorInput = new EditBox(this.font, x + 10, currentY, 380, 20, Component.translatable("labyrinthmod.quest_admin.author"));
        authorInput.setHint(Component.translatable("labyrinthmod.quest_admin.author_hint"));
        this.addWidget(authorInput);
        currentY += 25;

        requiredItemInput = new EditBox(this.font, x + 10, currentY, 250, 20, Component.translatable("labyrinthmod.quest_admin.item"));
        requiredItemInput.setHint(Component.literal("minecraft:diamond"));
        this.addWidget(requiredItemInput);

        requiredCountInput = new EditBox(this.font, x + 270, currentY, 120, 20, Component.translatable("labyrinthmod.quest_admin.count"));
        requiredCountInput.setHint(Component.literal("1"));
        this.addWidget(requiredCountInput);
        currentY += 25;

        this.addRenderableWidget(Button.builder(Component.translatable("labyrinthmod.quest_admin.add_item"), btn -> {
            String itemId = requiredItemInput.getValue().trim();
            String countStr = requiredCountInput.getValue().trim();
            if (!itemId.isEmpty()) {
                int count = 1;
                try { count = Integer.parseInt(countStr); if (count < 1) count = 1; } catch (NumberFormatException e) {}
                requiredItems.add(new SaveQuestToJsonPacket.RequiredItemData(itemId, count));
                requiredItemInput.setValue("");
                requiredCountInput.setValue("");
            }
        }).bounds(x + 10, currentY, 380, 20).build());
        currentY += 25;

        this.addRenderableWidget(Button.builder(Component.translatable("labyrinthmod.quest_admin.save"), btn -> saveQuest())
                .bounds(x + 10, currentY, 380, 20).build());
        currentY += 25;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), btn -> this.onClose())
                .bounds(x + 10, currentY, 380, 20).build());
    }

    private void saveQuest() {
        String title = titleInput.getValue().isEmpty() ? Component.translatable("labyrinthmod.quest_admin.default_title").getString() : titleInput.getValue();
        String description = descriptionInput.getValue().isEmpty() ? Component.translatable("labyrinthmod.quest_admin.default_description").getString() : descriptionInput.getValue();
        String author = authorInput.getValue().isEmpty() ? Component.translatable("labyrinthmod.quest_admin.default_author").getString() : authorInput.getValue();

        // Отправляем пакет на сервер для сохранения в JSON
        NetworkHandler.CHANNEL.sendToServer(new SaveQuestToJsonPacket(title, description, author, requiredItems));

        this.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int x = (this.width - SCREEN_WIDTH) / 2;
        int y = (this.height - SCREEN_HEIGHT) / 2;

        guiGraphics.fill(x, y, x + SCREEN_WIDTH, y + SCREEN_HEIGHT, 0xCC000000);
        guiGraphics.renderOutline(x, y, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFF555555);

        String title = Component.translatable("labyrinthmod.quest_admin.title").getString();
        guiGraphics.drawString(this.font, title, x + (SCREEN_WIDTH - this.font.width(title)) / 2, y + 5, 0xFFFFFF, true);

        // Отображение добавленных предметов
        if (!requiredItems.isEmpty()) {
            int reqY = y + 160;
            guiGraphics.drawString(this.font, Component.translatable("labyrinthmod.quest_admin.required"), x + 10, reqY, 0xFFD700);
            reqY += 12;
            for (int i = 0; i < requiredItems.size() && i < 4; i++) {
                SaveQuestToJsonPacket.RequiredItemData item = requiredItems.get(i);
                guiGraphics.drawString(this.font, item.count + "x " + item.itemId, x + 10, reqY, 0xAAAAAA);
                reqY += 10;
            }
        }

        titleInput.render(guiGraphics, mouseX, mouseY, partialTick);
        descriptionInput.render(guiGraphics, mouseX, mouseY, partialTick);
        authorInput.render(guiGraphics, mouseX, mouseY, partialTick);
        requiredItemInput.render(guiGraphics, mouseX, mouseY, partialTick);
        requiredCountInput.render(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
