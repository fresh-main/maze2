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
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class CompleteTaskPacket {
    private final int slotIndex;

    public CompleteTaskPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public static void encode(CompleteTaskPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.slotIndex);
    }

    public static CompleteTaskPacket decode(FriendlyByteBuf buf) {
        return new CompleteTaskPacket(buf.readInt());
    }

    public static void handle(CompleteTaskPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ItemStack stack = ItemStack.EMPTY;
                int targetSlot = msg.slotIndex;

                // Пытаемся получить предмет по указанному слоту
                if (targetSlot >= 0 && targetSlot < player.getInventory().getContainerSize()) {
                    ItemStack invStack = player.getInventory().getItem(targetSlot);
                    if (invStack.getItem() instanceof TaskScrollItem && invStack.hasTag()) {
                        stack = invStack;
                    }
                }

                // Если не нашли (игрок мог переложить предмет), ищем любой свиток с заданием в инвентаре
                if (stack.isEmpty()) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = player.getInventory().getItem(i);
                        if (invStack.getItem() instanceof TaskScrollItem && invStack.hasTag()) {
                            stack = invStack;
                            targetSlot = i;
                            break;
                        }
                    }
                }

                if (!stack.isEmpty() && stack.hasTag()) {
                    CompoundTag tag = stack.getTag();
                    if (tag.contains("RequiredItems", Tag.TAG_LIST)) {
                        ListTag requiredItems = tag.getList("RequiredItems", Tag.TAG_COMPOUND);
                        boolean hasAllItems = true;

                        // 1. Проверяем наличие всех требуемых предметов
                        for (int i = 0; i < requiredItems.size(); i++) {
                            CompoundTag itemTag = requiredItems.getCompound(i);
                            String itemId = itemTag.getString("ItemId");
                            int count = itemTag.getInt("Count");

                            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
                            if (item == null || player.getInventory().countItem(item) < count) {
                                hasAllItems = false;
                                break;
                            }
                        }

                        if (hasAllItems) {
                            // 1. Удаляем требуемые предметы из инвентаря (код из предыдущего шага)
                            for (int i = 0; i < requiredItems.size(); i++) {
                                CompoundTag itemTag = requiredItems.getCompound(i);
                                String itemId = itemTag.getString("ItemId");
                                int count = itemTag.getInt("Count");
                                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));

                                if (item != null) {
                                    int remaining = count;
                                    for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                                        ItemStack invStack = player.getInventory().getItem(slot);
                                        if (invStack.is(item)) {
                                            int toRemove = Math.min(remaining, invStack.getCount());
                                            invStack.shrink(toRemove);
                                            remaining -= toRemove;
                                        }
                                    }
                                }
                            }

                            // 2. Выдаем награду в виде опыта
                            int xpReward = tag.getInt("XpReward");
                            if (xpReward > 0) {
                                player.giveExperienceLevels(xpReward);
                                player.sendSystemMessage(Component.literal("§aЗадание выполнено! Вы получили " + xpReward + " уровней опыта."));
                            } else {
                                player.sendSystemMessage(Component.literal("§aЗадание выполнено!"));
                            }

                            // 3. Удаляем свиток из инвентаря
                            player.getInventory().removeItem(targetSlot, 1);
                            player.getInventory().setChanged();
                        } else {
                            player.sendSystemMessage(Component.literal("§cУ вас нет всех необходимых предметов для выполнения задания!"));
                        }
                    } else {
                        // Если требуемых предметов нет в теге, просто удаляем свиток
                        player.getInventory().removeItem(targetSlot, 1);
                        player.getInventory().setChanged();
                        player.sendSystemMessage(Component.literal("§aЗадание выполнено!"));
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}