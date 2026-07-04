package com.labyrinthmod.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class CraftRestrictionMenu extends AbstractContainerMenu {
    // Убираем final, чтобы избежать ошибок инициализации в конструкторе с буфером
    private ItemStack targetItem;

    public CraftRestrictionMenu(int containerId, Inventory playerInv, FriendlyByteBuf buf) {
        this(containerId, playerInv, ItemStack.EMPTY);

        if (buf != null) {
            ResourceLocation id = buf.readResourceLocation();
            if (id != null && !id.equals(ResourceLocation.tryParse("minecraft:air"))) {
                var item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null) {
                    this.targetItem = new ItemStack(item);
                }
            }
        }
    }

    public CraftRestrictionMenu(int containerId, Inventory playerInv, ItemStack target) {
        super(com.labyrinthmod.common.init.ModMenuTypes.CRAFT_RESTRICTION_MENU.get(), containerId);
        this.targetItem = target.isEmpty() ? ItemStack.EMPTY : target;

        // Слот для целевого предмета (Перенесен влево: x=45, y=45)
        this.addSlot(new Slot(new net.minecraft.world.SimpleContainer(1), 0, 45, 45) {
            @Override
            public boolean mayPlace(ItemStack stack) { return true; }

            @Override
            public void set(ItemStack stack) {
                super.set(stack);
                if (!stack.isEmpty() && playerInv.player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    var forbidden = com.labyrinthmod.common.data.CraftRestrictionManager.getForbiddenFactions(stack.getItem());
                    com.labyrinthmod.common.network.NetworkHandler.CHANNEL.send(
                            net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sp),
                            new com.labyrinthmod.common.network.packet.S2CSyncCraftRestrictionsPacket(
                                    ForgeRegistries.ITEMS.getKey(stack.getItem()), forbidden
                            )
                    );
                }
            }
        });

        // Инвентарь игрока (Центрирован внизу)
        // Ширина инвентаря = 162. Центрирование для ширины меню 340: (340 - 162) / 2 = 89
        int invStartX = 89;

        for(int row = 0; row < 3; ++row) {
            for(int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, invStartX + col * 18, 155 + row * 18));
            }
        }
        // Хотбар
        for(int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInv, col, invStartX + col * 18, 213));
        }
    }

    public ItemStack getTargetItem() { return targetItem; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public boolean stillValid(Player player) { return true; }
}