package com.labyrinthmod.client.gui;

import com.labyrinthmod.common.capability.FractionType;
import com.labyrinthmod.common.event.FractionSwitcherData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class FractionSwitchOverlay {

    private static int selectedIndex = 0;
    private static boolean showOverlay = false;

    public static final IGuiOverlay OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (!showOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        FractionType[] fractions = FractionType.values();
        int fractionCount = fractions.length;

        // Размеры слотов (предмет 16x16, слот 28x28)
        int slotSize = 28;
        int spacing = 6;
        int totalWidth = fractionCount * (slotSize + spacing) - spacing;
        int startX = (screenWidth - totalWidth) / 2;
        int startY = screenHeight / 2 - slotSize / 2 - 10; // Чуть выше центра

        // Полупрозрачный фон
        guiGraphics.fill(startX - 6, startY - 6,
                startX + totalWidth + 6, startY + slotSize + 6,
                0xAA000000);

        for (int i = 0; i < fractionCount; i++) {
            FractionType fraction = fractions[i];
            int x = startX + i * (slotSize + spacing);
            boolean isSelected = (i == selectedIndex);

            // Рамка слота (Желтая если выбрана, серая если нет)
            int borderColor = isSelected ? 0xFFFFFF00 : 0xFF555555;
            guiGraphics.fill(x, startY, x + slotSize, startY + slotSize, borderColor);
            guiGraphics.fill(x + 1, startY + 1, x + slotSize - 1, startY + slotSize - 1, 0x80000000);

            // Иконка предмета (центрируем 16x16 внутри 28x28)
            ItemStack icon = FractionSwitcherData.getIcon(fraction);
            if (!icon.isEmpty()) {
                guiGraphics.renderItem(icon, x + 6, startY + 6);
            }
        }

        // Подсказка снизу
        Component instruction = Component.literal("Отпустите F3 для применения");
        int instructionWidth = mc.font.width(instruction);
        guiGraphics.drawString(mc.font, instruction, (screenWidth - instructionWidth) / 2, startY + slotSize + 12, 0xFFFFFF, true);
    };

    public static void showOverlay() {
        showOverlay = true;
        selectedIndex = 0;
    }

    public static void nextFraction() {
        FractionType[] fractions = FractionType.values();
        selectedIndex = (selectedIndex + 1) % fractions.length;
    }

    public static FractionType getSelectedFraction() {
        return FractionType.values()[selectedIndex];
    }

    public static void hideOverlay() {
        showOverlay = false;
    }

    public static boolean isShowing() {
        return showOverlay;
    }
}