package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.item.TaskScrollItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class CompleteScrollPacket {

    private final int slot;

    public CompleteScrollPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(CompleteScrollPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.slot);
    }

    public static CompleteScrollPacket decode(FriendlyByteBuf buf) {
        return new CompleteScrollPacket(buf.readInt());
    }

    public static void handle(CompleteScrollPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            int slot = packet.slot;

            // Защита от неправильного слота
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
                player.sendSystemMessage(Component.literal("§cОшибка: неверный слот свитка."));
                return;
            }

            ItemStack scroll = player.getInventory().getItem(slot);

            if (scroll.isEmpty() || !(scroll.getItem() instanceof TaskScrollItem)) {
                player.sendSystemMessage(Component.literal("§cСвиток задания не найден."));
                return;
            }

            if (!scroll.hasTag()) {
                player.sendSystemMessage(Component.literal("§cСвиток не содержит данных задания."));
                return;
            }

            CompoundTag tag = scroll.getTag();

            if (tag.getBoolean("Completed")) {
                player.sendSystemMessage(Component.literal("§cЭто задание уже выполнено."));
                return;
            }

            if (!tag.contains("RequiredItems", Tag.TAG_LIST)) {
                // Если нет требуемых предметов, просто выполняем
                tag.putBoolean("Completed", true);
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                player.sendSystemMessage(Component.literal("§aЗадание выполнено!"));
                return;
            }

            ListTag requiredItems = tag.getList("RequiredItems", Tag.TAG_COMPOUND);

            // Проверка предметов
            for (int i = 0; i < requiredItems.size(); i++) {
                CompoundTag itemTag = requiredItems.getCompound(i);

                String itemId = getRequiredItemId(itemTag);
                int count = getRequiredItemCount(itemTag);

                if (itemId.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§cВ задании указан пустой предмет."));
                    return;
                }

                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl == null) {
                    player.sendSystemMessage(Component.literal("§cНекорректный предмет: " + itemId));
                    return;
                }

                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null || item == Items.AIR) {
                    player.sendSystemMessage(Component.literal("§cНеизвестный предмет: " + itemId));
                    return;
                }

                int has = countItem(player, item);

                if (has < count) {
                    player.sendSystemMessage(Component.literal("§cНе хватает предметов для выполнения задания."));
                    return;
                }
            }

            // Если дошли сюда — предметов хватает, списываем предметы
            for (int i = 0; i < requiredItems.size(); i++) {
                CompoundTag itemTag = requiredItems.getCompound(i);

                String itemId = getRequiredItemId(itemTag);
                int count = getRequiredItemCount(itemTag);

                ResourceLocation rl = ResourceLocation.tryParse(itemId);
                if (rl == null) continue;

                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null || item == Items.AIR) continue;

                removeItems(player, item, count);
            }

            // Удаляем свиток из инвентаря
            player.getInventory().setItem(slot, ItemStack.EMPTY);

            player.sendSystemMessage(Component.literal("§aЗадание выполнено!"));
        });

        context.setPacketHandled(true);
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static void removeItems(ServerPlayer player, Item item, int count) {
        int remaining = count;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (remaining <= 0) break;

            ItemStack stack = player.getInventory().getItem(i);

            if (stack.isEmpty()) continue;
            if (stack.getItem() != item) continue;

            int toRemove = Math.min(stack.getCount(), remaining);
            stack.shrink(toRemove);
            remaining -= toRemove;

            if (stack.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
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
            if (tag.contains(key, Tag.TAG_STRING)) {
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
}