package com.labyrinthmod.common.network.packet;

import com.labyrinthmod.common.item.TaskScrollItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class PlaceItemPacket {
    private final BlockPos boardPos;

    public PlaceItemPacket(BlockPos boardPos) {
        this.boardPos = boardPos;
    }

    public static void encode(PlaceItemPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.boardPos);
    }

    public static PlaceItemPacket decode(FriendlyByteBuf buf) {
        return new PlaceItemPacket(buf.readBlockPos());
    }

    public static void handle(PlaceItemPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                // Ищем свиток в инвентаре игрока
                boolean completed = false;

                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack invItem = player.getInventory().getItem(i);

                    if (!invItem.isEmpty() && invItem.is(TaskScrollItem.TASK_SCROLL.get()) && invItem.hasTag()) {
                        CompoundTag scrollTag = invItem.getTag();

                        // Проверяем что свиток не выполнен
                        if (scrollTag.getBoolean("Completed")) {
                            continue;
                        }

                        String title = scrollTag.getString("Title");
                        String reward = scrollTag.getString("Reward");

                        // Получаем список требуемых предметов
                        ListTag requiredItems = scrollTag.getList("RequiredItems", net.minecraft.nbt.Tag.TAG_COMPOUND);

                        if (requiredItems.isEmpty()) {
                            continue; // Нет требуемых предметов
                        }

                        // Проверяем все требуемые предметы
                        boolean allItemsPresent = true;
                        for (int j = 0; j < requiredItems.size(); j++) {
                            CompoundTag reqItemTag = requiredItems.getCompound(j);
                            String requiredItemId = reqItemTag.getString("ItemId");
                            int requiredCount = reqItemTag.getInt("Count");

                            if (requiredItemId.isEmpty()) continue;

                            ResourceLocation reqId = ResourceLocation.tryParse(requiredItemId);
                            if (reqId == null) continue;

                            net.minecraft.world.item.Item reqItem = ForgeRegistries.ITEMS.getValue(reqId);
                            if (reqItem == null) continue;

                            // Считаем сколько этого предмета у игрока
                            int playerCount = 0;
                            for (int k = 0; k < player.getInventory().getContainerSize(); k++) {
                                ItemStack playerItem = player.getInventory().getItem(k);
                                if (!playerItem.isEmpty() && playerItem.is(reqItem)) {
                                    playerCount += playerItem.getCount();
                                }
                            }

                            if (playerCount < requiredCount) {
                                allItemsPresent = false;
                                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                        "§cНедостаточно " + requiredItemId + ": нужно " + requiredCount + ", есть " + playerCount));
                                break;
                            }
                        }

                        if (!allItemsPresent) {
                            continue;
                        }

                        // Все предметы есть! Забираем их
                        for (int j = 0; j < requiredItems.size(); j++) {
                            CompoundTag reqItemTag = requiredItems.getCompound(j);
                            String requiredItemId = reqItemTag.getString("ItemId");
                            int requiredCount = reqItemTag.getInt("Count");

                            ResourceLocation reqId = ResourceLocation.tryParse(requiredItemId);
                            if (reqId == null) continue;

                            net.minecraft.world.item.Item reqItem = ForgeRegistries.ITEMS.getValue(reqId);
                            if (reqItem == null) continue;

                            // Забираем нужное количество
                            int remaining = requiredCount;
                            for (int k = 0; k < player.getInventory().getContainerSize() && remaining > 0; k++) {
                                ItemStack playerItem = player.getInventory().getItem(k);
                                if (!playerItem.isEmpty() && playerItem.is(reqItem)) {
                                    int takeCount = Math.min(playerItem.getCount(), remaining);
                                    playerItem.shrink(takeCount);
                                    remaining -= takeCount;
                                }
                            }
                        }

                        // Помечаем свиток как выполненный
                        scrollTag.putBoolean("Completed", true);
                        invItem.setTag(scrollTag);

                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a✓ Задание выполнено: " + title));
                        if (!reward.isEmpty()) {
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Награда: " + reward));
                        }
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Свиток остался в инвентаре как память"));

                        completed = true;
                        break;
                    }
                }

                if (!completed) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cНет подходящего задания или недостаточно предметов"));
                }
            }
        });
        context.setPacketHandled(true);
    }
}