package com.labyrinthmod.client.gui;

import com.labyrinthmod.common.network.NetworkHandler;
import com.labyrinthmod.common.network.packet.C2SSaveCraftRestrictionsPacket;
import com.labyrinthmod.gui.CraftRestrictionMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public class CraftRestrictionScreen extends AbstractContainerScreen<CraftRestrictionMenu> {
    private static final String[] FACTIONS = {"FARMER", "BUTCHER", "RUNNER", "COOK", "MEDIC", "OPERATOR", "NONE"};
    private final Set<String> checkedFactions = new HashSet<>();

    public CraftRestrictionScreen(CraftRestrictionMenu menu, Inventory inv, Component title) {
        super(menu, inv, Component.literal("Управление крафтом"));
        // ★ УВЕЛИЧЕННЫЕ РАЗМЕРЫ ★
        this.imageWidth = 340;
        this.imageHeight = 250;

        // Подгоняем подписи инвентаря под новые координаты
        this.inventoryLabelX = 89;
        this.inventoryLabelY = 143;
        this.titleLabelX = 20;
        this.titleLabelY = 15;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        // Кнопка "Сохранить" (Увеличена по ширине и высоте)
        this.addRenderableWidget(Button.builder(Component.literal("Сохранить"), (btn) -> {
            ItemStack stack = this.menu.getSlot(0).getItem();
            if (!stack.isEmpty()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                NetworkHandler.CHANNEL.sendToServer(new C2SSaveCraftRestrictionsPacket(itemId, new HashSet<>(checkedFactions)));
                this.minecraft.player.sendSystemMessage(Component.literal("§aНастройки крафта сохранены!"));
            }
        }).bounds(x + 20, y + 85, 100, 24).build());

        // Кнопка "Сбросить"
        this.addRenderableWidget(Button.builder(Component.literal("Сбросить"), (btn) -> {
            ItemStack stack = this.menu.getSlot(0).getItem();
            if (!stack.isEmpty()) {
                checkedFactions.clear();
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                NetworkHandler.CHANNEL.sendToServer(new C2SSaveCraftRestrictionsPacket(itemId, new HashSet<>()));
                this.minecraft.player.sendSystemMessage(Component.literal("§eЗапреты сброшены!"));
            }
        }).bounds(x + 20, y + 115, 100, 24).build());
    }

    public void updateCheckedFactions(Set<String> forbidden) {
        this.checkedFactions.clear();
        this.checkedFactions.addAll(forbidden);
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int right = this.leftPos + this.imageWidth;
        int bottom = this.topPos + this.imageHeight;

        // ★ УВЕЛИЧЕННЫЙ ФОН ★
        gfx.fill(this.leftPos, this.topPos, right, bottom, 0xFFC6C6C6);

        // Толстые рамки для нового размера
        gfx.fill(this.leftPos, this.topPos, right, this.topPos + 2, 0xFF373737);
        gfx.fill(this.leftPos, this.topPos, this.leftPos + 2, bottom, 0xFF373737);
        gfx.fill(this.leftPos, bottom - 2, right, bottom, 0xFFFFFFFF);
        gfx.fill(right - 2, this.topPos, right, bottom, 0xFFFFFFFF);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);

        int x = this.leftPos;
        int y = this.topPos;

        // Заголовок для списка фракций (СПРАВА)
        gfx.drawString(this.font, "Запрет крафта для фракций:", x + 160, y + 20, 0x404040, false);

        // Рисуем галочки (Чекбоксы) справа с увеличенным шагом
        for (int i = 0; i < FACTIONS.length; i++) {
            String faction = FACTIONS[i];
            int boxX = x + 160;
            int boxY = y + 35 + (i * 18); // ★ ШАГ УВЕЛИЧЕН ДО 18 ★
            boolean isChecked = checkedFactions.contains(faction);

            // Рамка чекбокса
            gfx.fill(boxX, boxY, boxX + 10, boxY + 10, 0xFF000000);
            gfx.fill(boxX + 1, boxY + 1, boxX + 9, boxY + 9, 0xFF8B8B8B);

            if (isChecked) {
                gfx.fill(boxX + 2, boxY + 2, boxX + 8, boxY + 8, 0xFF00AA00);
            }

            int textColor = isChecked ? 0xAA0000 : 0x404040;
            gfx.drawString(this.font, faction, boxX + 16, boxY + 1, textColor, false);
        }

        this.renderTooltip(gfx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;

        for (int i = 0; i < FACTIONS.length; i++) {
            String faction = FACTIONS[i];
            int boxX = x + 160;
            int boxY = y + 35 + (i * 18);

            // Зона клика увеличена, чтобы было легко попасть мышкой
            if (mouseX >= boxX && mouseX <= boxX + 10 + this.font.width(faction) + 16 &&
                    mouseY >= boxY && mouseY <= boxY + 14) {

                if (checkedFactions.contains(faction)) {
                    checkedFactions.remove(faction);
                } else {
                    checkedFactions.add(faction);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}